package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;

import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.riskanalysis.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.BidRecordCredentialConversionStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.CredentialMarkdownAggregationService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileConvertService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;

import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.RiskAnalysisMapper;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 风险分析业务服务。同步调用 OpenAI 兼容 LM Studio，通过 Structured Output JSON Schema 约束输出，
 * 再解析并落库风险分析结果。
 *
 * <p>风险分析开始前先通过 {@code bid_record.risk_analysis_status} 原子抢占分析权；
 * 写入 {@code risk_analysis} 和将投标记录标记为完成时通过 {@link #self} 走 Spring 代理，
 * 保证两次数据库写入处于同一事务。</p>
 *
 * @since V18
 */
@Service
public class RiskAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalysisService.class);
    private static final String PROMPT_VERSION = "v1";

    /** 风险分析模型的固定 system prompt，与当前 Structured Output schema 配套。 */
    private static final String SYSTEM_PROMPT =
            """
            你是一位专业的政府采购风险分析师。请根据提供的供应商信息，从以下维度进行风险评估：

            1. 资质合规性
            2. 报价合理性
            3. 历史履约情况
            4. 关联风险（关联供应商、同一控制人等）

            请以 JSON 格式返回分析结果，包含以下字段：
            - riskLevel: 风险等级（LOW / MEDIUM / HIGH / CRITICAL）
            - summary: 风险摘要（200字以内）
            - suggestions: 建议措施（100字以内）

            只返回 JSON，不要包含任何其他文字说明。

            输出格式（严格）：
            - 仅输出一个 JSON 对象，字段完全对齐 schema（riskLevel ∈ {LOW, MEDIUM, HIGH, CRITICAL}）
            - 不得前置思考/推理/解释过程
            - 不得使用 ```json``` 围栏或其他任何包裹
            - 不得输出尾随文字
            """;

    /** 风险分析模型的固定 user prompt；仅业务字段使用 {@link String#formatted} 注入。 */
    private static final String USER_PROMPT_TEMPLATE =
            """
            请分析以下投标人的风险情况：

            ## 投标人信息
            - 投标人名称：%s
            - 报价：%s元
            - 联系方式：%s
            - 资信材料：%s

            %s
            """;

    /**
     * Structured Output JSON Schema，约束 LLM 仅输出符合此结构的 JSON 对象。
     * 通过 {@link OpenAiChatOptions.Builder#responseFormat(ResponseFormat)} 传给下游 OpenAI 兼容服务。
     * 字段定义与 {@link RiskAnalysis} 实体保持一致，riskLevel 限定 4 档枚举。
     */
    private static final String OUTPUT_SCHEMA =
            """
            {
              "type": "object",
              "properties": {
                "riskLevel":   {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"]},
                "summary":     {"type": "string"},
                "suggestions": {"type": "string"}
              },
              "required": ["riskLevel", "summary", "suggestions"],
              "additionalProperties": false
            }
            """;

    private final ChatClient chatClient;

    /**
     * 自注入 Spring 代理，用于绕过 {@code @Transactional} 同类自调失效。
     * <p>{@code @Lazy} 避免循环依赖(Spring 实例化本 Bean 时尚无代理可用,需延后注入)。</p>
     */
    @Resource
    @Lazy
    private RiskAnalysisService self;

    @Resource
    private BidRecordLoader loader;

    @Resource
    private RiskAnalysisMapper mapper;

    @Resource
    private BidRecordMapper bidRecordMapper;

    @Resource
    private CredentialMarkdownAggregationService markdownAggregationService;

    @Resource
    private FileConvertService fileConvertService;

    @Resource
    private CredentialFileMapper credentialFileMapper;

    @Resource
    private FileStorage fileStorage;

    /**
     * 构造风险分析 ChatClient，并把 Structured Output JSON Schema 固化到默认调用参数中。
     *
     * @param openAiChatModel Spring AI OpenAI 兼容聊天模型 Bean
     */
    public RiskAnalysisService(OpenAiChatModel openAiChatModel) {
        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(OUTPUT_SCHEMA)
                .build();
        OpenAiChatOptions.Builder defaults = OpenAiChatOptions.builder()
                .responseFormat(responseFormat);
        this.chatClient = ChatClient.builder(openAiChatModel)
                .defaultOptions(defaults)
                .build();
    }

    /**
     * 触发一次风险分析。同步调用 OpenAI 兼容 LM Studio API，Structured Output 约束 schema 后 JSON 解析填字段，落库并返全字段实体。
     *
     * @param bidRecordId 投标记录 ID
     * @return 含 analysisId 的完整 RiskAnalysis
     * @throws AppException 投标记录不存在、模型调用失败或模型响应不合法
     */
    public RiskAnalysis analyzeRisk(Integer bidRecordId) {
        log.info(">>> [风险分析] 开始 bidRecordId={}", bidRecordId);

        BidRecord record = bidRecordMapper.selectById(bidRecordId);
        if (record == null) {
            log.warn("<<< [风险分析] 投标记录不存在 bidRecordId={}", bidRecordId);
            throw new AppException(
                    AppErrorCode.BID_RECORD_NOT_FOUND,
                    "risk-analysis",
                    "ANALYZE_RISK",
                    "LOAD_BID_RECORD",
                    "投标记录不存在: " + bidRecordId);
        }

        if (bidRecordMapper.claimRiskAnalysis(
                bidRecordId,
                LocalDateTime.now()) != 1) {
            throw new AppException(
                    AppErrorCode.RISK_ANALYSIS_IN_PROGRESS,
                    "risk-analysis",
                    "ANALYZE_RISK",
                    "CLAIM_ANALYSIS",
                    "该投标记录正在进行风险分析，请稍后再试: " + bidRecordId);
        }

        try {
            return doAnalyzeRisk(bidRecordId, record);
        } catch (RuntimeException failure) {
            markRiskAnalysisFailed(bidRecordId, failure);
            throw failure;
        }
    }

    /** 执行已经成功抢占分析权后的汇总、模型调用和结果落库流程。 */
    private RiskAnalysis doAnalyzeRisk(Integer bidRecordId, BidRecord record) {

        log.info(">>> [风险分析] 投标人信息 name={} quotePrice={} contact={}",
                record.getBidderName(), record.getQuotePrice(), record.getBidderContact());

        ensureCredentialFilesConverted(bidRecordId);
        String mergedMarkdownPath = markdownAggregationService.aggregate(bidRecordId);
        String mergedMarkdown = readMergedMarkdown(mergedMarkdownPath);
        String detailInfo = loader.loadDetail(bidRecordId);
        String userPrompt = USER_PROMPT_TEMPLATE.formatted(
                record.getBidderName() != null ? record.getBidderName() : "",
                record.getQuotePrice() != null ? record.getQuotePrice().toString() : "",
                record.getBidderContact() != null ? record.getBidderContact() : "",
                mergedMarkdown,
                detailInfo);

        log.info(">>> [风险分析] 发送 Prompt:\nsystem=\n{}\nuser=\n{}", SYSTEM_PROMPT, userPrompt);

        long startNs = System.nanoTime();

        // 调用模型,获取完整 ChatResponse(防止内容回复从 content 落入 reasoningContent,见 extractRawContent JavaDoc)
        ChatResponse chatResponse;
        try {
            chatResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            throw new AppException(
                    AppErrorCode.RISK_ANALYSIS_LLM_CALL_FAILED,
                    "risk-analysis",
                    "ANALYZE_RISK",
                    "LLM_CALL",
                    "风险分析模型调用失败",
                    e);
        }
        String raw = extractRawContent(chatResponse);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        LocalDateTime analyzedAt = LocalDateTime.now();

        log.info("<<< [风险分析] AI 响应 (length={}, elapsed={}ms, analyzedAt={}):\n{}",
                raw != null ? raw.length() : 0, elapsedMs, analyzedAt, raw);

        RiskAnalysis analysis = parseAndBuild(bidRecordId, record.getBidderName(), raw, analyzedAt);

        RiskAnalysis saved = self.persistAndComplete(analysis, bidRecordId);
        log.info("<<< [风险分析] 落库成功 analysisId={}", saved.getAnalysisId());
        return saved;
    }

    /**
     * 风险分析入口的资信材料准备判断。
     *
     * <p>前端只调用风险分析入口。若当前投标记录存在尚未转换完成的批次或文件，
     * 由 Java 直接调用现有无差别转换流程；转换后重新查询数据库，只有全部文件完成
     * 转换后才允许继续汇总和调用大模型。</p>
     */
    private void ensureCredentialFilesConverted(Integer bidRecordId) {
        List<CredentialFile> files =
                credentialFileMapper.selectByBidRecordId(bidRecordId);
        if (files == null || files.isEmpty()) {
            throw inputNotReady("该投标记录暂无资信材料，不能进行风险分析");
        }

        if (areCredentialFilesConverted(files)) {
            return;
        }

        log.info("[风险分析] 资信材料未全部转换，自动发起转换 bidRecordId={}",
                bidRecordId);
        FileConvertResponse response =
                fileConvertService.convertAll(bidRecordId);

        // 转换服务返回的是文件汇总结果，仍需重新查询文件状态，避免只依据响应快照继续分析。
        List<CredentialFile> latestFiles =
                credentialFileMapper.selectByBidRecordId(bidRecordId);
        if (response == null
                || response.status() != BidRecordCredentialConversionStatus.ALL_CONVERTED
                || !areCredentialFilesConverted(latestFiles)) {
            throw inputNotReady("资信材料尚未全部转换完成，暂不能进行风险分析");
        }
    }

    /** 判断指定投标记录下的文件是否都已经具备可分析的 Markdown。 */
    private boolean areCredentialFilesConverted(
            List<CredentialFile> files) {
        if (files == null || files.isEmpty()) {
            return false;
        }
        return files.stream().allMatch(file -> file != null
                && file.getConvertStatus() == ConvertStatus.CONVERTED
                && file.getMarkdownFilePath() != null
                && !file.getMarkdownFilePath().isBlank());
    }

    /** 构造风险分析输入未准备完成异常。 */
    private AppException inputNotReady(String message) {
        return new AppException(
                AppErrorCode.RISK_ANALYSIS_INPUT_NOT_READY,
                "risk-analysis",
                "ANALYZE_RISK",
                "PREPARE_CREDENTIAL_FILES",
                message);
    }

    /** 将当前风险分析标记为失败，失败状态允许后续请求重试。 */
    private void markRiskAnalysisFailed(
            Integer bidRecordId,
            RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = "风险分析失败";
        }
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        int rows = bidRecordMapper.markRiskAnalysisFailed(bidRecordId, message);
        if (rows != 1) {
            log.warn("[风险分析] 失败状态回写失败 bidRecordId={}", bidRecordId);
        }
    }

    /** 读取汇总服务写入的投标记录级 Markdown，作为本次唯一的资信材料输入。 */
    private String readMergedMarkdown(String mergedMarkdownPath) {
        try (InputStream input = fileStorage.open(mergedMarkdownPath)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AppException(
                    AppErrorCode.RISK_ANALYSIS_MARKDOWN_AGGREGATION_FAILED,
                    "risk-analysis",
                    "ANALYZE_RISK",
                    "READ_MERGED_MARKDOWN",
                    "资信材料汇总文件读取失败",
                    e);
        }
    }

    /**
     * 从 ChatResponse 抽取 LLM 原始文本输出。
     *
     * <p><b>为什么需要这个方法</b>:某些 Hybrid Reasoning(混合推理)模型
     * (实测代表: <code>huihui-qwen3.6-35b-a3b-claude-4.7-opus-abliterated-mlx</code>)
     * 在启用了 {@code response_format=json_schema} 的情况下,仍然把"实际 JSON 输出"写进
     * OpenAI 兼容响应的 <code>reasoning_content</code> 字段,而不是 <code>content</code>
     * 字段。LM Studio 实际日志:</p>
     *
     * <pre>
     * "choices": [{
     *   "message": {
     *     "role": "assistant",
     *     "content": "",                                          // ← 空
     *     "reasoning_content": "{\"riskLevel\": \"MEDIUM\", ...}"  // ← JSON 在这里
     *   },
     *   "finish_reason": "stop"
     * }]
     * </pre>
     *
     * <p>如果仅用 {@code chatClient.call().content()},会读到空字符串 → {@code parseAndBuild}
     * 拿到 null → DB 写入撞 <code>risk_level</code> NOT NULL 约束,产生 500 内部服务错误。</p>
     *
     * <p><b>为什么取 {@code AssistantMessage.metadata["reasoningContent"]} 能 work</b>:
     * 反编译 <code>spring-ai-openai-2.0.0.jar</code> 确认 {@code OpenAiChatModel}
     * 内部已经实现了这个透传:</p>
     *
     * <ol>
     *   <li>{@code OpenAiChatModel.getReasoningContent(Choice)} 私有方法从
     *       {@code ChatCompletionMessage._additionalProperties().get("reasoning_content")}
     *       读取(OpenAI 官方字段不在 POJO schema 中,只有 additionalProperties 能兜底)</li>
     *   <li>{@code OpenAiChatModel.internalCall} 把读到的 reasoning 字符串塞进 7 键 Map,
     *       交给 {@code buildGeneration} 构造 {@code AssistantMessage},作为 metadata 一员</li>
     *   <li>map key 是常量 {@code private static final String REASONING_CONTENT = "reasoningContent"};
     *       (注意:snake_case 的 JSON 字段在 Spring AI 透传时被改写成 camelCase)</li>
     * </ol>
     *
     * <p>所以 <b>不需要</b>降级到 raw HTTP 调用、也不需要绕开 Spring AI,
     * 只需要从 {@code ChatResponse} 层级拿 {@code metadata} 即可。</p>
     *
     * <p>降级路径触发时打 INFO 日志(<i>"降级到 reasoningContent"</i>),便于排查"为什么我 prompt 没变,
     * JSON 却在 reasoning 字段里"的偶发问题 — 一般表明当前模型版本不遵守 Structured Output。</p>
     *
     * @param response Spring AI {@link ChatClient#call()} 返回的 {@link ChatResponse},可为 {@code null}
     * @return 优先 {@code AssistantMessage.getContent()},为空时降级
     *         {@code metadata["reasoningContent"]},全空返 {@code ""}
     */
    private String extractRawContent(ChatResponse response) {
        if (response == null) {
            return "";
        }
        Generation gen = response.getResult();
        if (gen == null) {
            return "";
        }
        AssistantMessage msg = gen.getOutput();
        if (msg == null) {
            return "";
        }
        // 取 content 字段
        String content = msg.getText();
        if (content != null && !content.isBlank()) {
            return content;
        }
        // 兜底:Hybrid Reasoning 模型把 JSON 输出塞进 reasoning_content。
        // 取 AssistantMessage.metadata 是因为 spring-ai-openai 2.0 内 OpenAiChatModel
        // 已把 reasoning_content 字段透传到这里,key="reasoningContent"(camelCase)。
        Map<String, Object> metadata = msg.getMetadata();
        if (metadata == null) {
            return "";
        }
        Object reasoning = metadata.get("reasoningContent");
        // 取 reasoning_content 字段
        if (reasoning != null && !reasoning.toString().isBlank()) {
            log.info("[风险分析] content 为空,降级到 reasoningContent (length={})",
                    reasoning.toString().length());
            return reasoning.toString();
        }
        return "";
    }

    /**
     * 解析 AI 原始 JSON 响应，构造 RiskAnalysis 实体。
     * <p>响应为空、JSON 无法解析、必填字段缺失或 riskLevel 不符合约定时，直接抛出
     * {@code RISK_ANALYSIS.INVALID_LLM_RESPONSE}，不进入数据库落库。</p>
     *
     * @param bidRecordId 投标记录 ID
     * @param bidderName  投标人名称
     * @param raw         AI 原始响应文本
     * @param analyzedAt  分析时间
     * @return RiskAnalysis 实体（未落库）
     */
    private RiskAnalysis parseAndBuild(Integer bidRecordId, String bidderName, String raw, LocalDateTime analyzedAt) {
        if (raw == null || raw.isBlank()) {
            throw invalidLlmResponse("EMPTY_RESPONSE", "风险分析模型返回内容为空", null);
        }

        final JSONObject obj;
        try {
            obj = JSON.parseObject(raw);
        } catch (JSONException e) {
            throw invalidLlmResponse("PARSE_RESPONSE", "风险分析模型返回内容不是有效 JSON", e);
        }
        if (obj == null) {
            throw invalidLlmResponse("PARSE_RESPONSE", "风险分析模型返回内容为空对象", null);
        }

        String riskLevel = obj.getString("riskLevel");
        String summary = obj.getString("summary");
        String suggestions = obj.getString("suggestions");
        if (isBlank(riskLevel) || isBlank(summary) || isBlank(suggestions)) {
            throw invalidLlmResponse(
                    "VALIDATE_RESPONSE",
                    "风险分析模型返回内容缺少必要字段",
                    null);
        }
        if (!isSupportedRiskLevel(riskLevel)) {
            throw invalidLlmResponse(
                    "VALIDATE_RESPONSE",
                    "风险分析模型返回的风险等级不合法",
                    null);
        }

        log.info(">>> [风险分析] JSON 解析成功 riskLevel={}", riskLevel);

        RiskAnalysis a = new RiskAnalysis();
        a.setBidRecordId(bidRecordId);
        a.setBidderName(bidderName);
        a.setRiskLevel(riskLevel);
        a.setSummary(summary);
        a.setSuggestions(suggestions);
        a.setRawResponse(raw);
        a.setPromptVersion(PROMPT_VERSION);
        a.setAnalyzedAt(analyzedAt);
        return a;
    }

    /** 判断模型输出是否包含风险分析协议约定的必填文本。 */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 校验模型输出的风险等级枚举。 */
    private boolean isSupportedRiskLevel(String riskLevel) {
        return "LOW".equals(riskLevel)
                || "MEDIUM".equals(riskLevel)
                || "HIGH".equals(riskLevel)
                || "CRITICAL".equals(riskLevel);
    }

    /** 构造模型响应不合法异常，禁止无效结果进入数据库。 */
    private AppException invalidLlmResponse(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.RISK_ANALYSIS_INVALID_LLM_RESPONSE,
                "risk-analysis",
                "ANALYZE_RISK",
                stage,
                message,
                cause);
    }

    /**
     * 落库风险分析记录，并将投标记录标记为完成。analysisId 通过 useGeneratedKeys 回填。
     *
     * @param analysis    待落库的风险分析实体
     * @param bidRecordId 投标记录 ID
     * @return 已落库实体（含回填后的 analysisId）
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskAnalysis persistAndComplete(
            RiskAnalysis analysis,
            Integer bidRecordId) {
        try {
            mapper.insert(analysis);
            int rows = bidRecordMapper.markRiskAnalysisCompleted(bidRecordId);
            if (rows != 1) {
                throw new AppException(
                        AppErrorCode.RISK_ANALYSIS_PERSISTENCE_FAILED,
                        "risk-analysis",
                        "ANALYZE_RISK",
                        "MARK_COMPLETED",
                        "风险分析完成状态更新失败");
            }
            return analysis;
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AppException(
                    AppErrorCode.RISK_ANALYSIS_PERSISTENCE_FAILED,
                    "risk-analysis",
                    "ANALYZE_RISK",
                    "PERSIST_RESULT",
                    "风险分析结果保存失败",
                    e);
        }
    }

    /**
     * 查询指定投标记录的历史分析记录（按时间倒序）。
     *
     * @param bidRecordId 投标记录 ID
     * @return 历史分析记录列表，无记录时返回空列表
     */
    public List<RiskAnalysis> getHistory(Integer bidRecordId) {
        return mapper.selectByBidRecordId(bidRecordId);
    }

    /**
     * 查询全部风险分析记录。配合 PageHelper 使用（ThreadLocal 注入分页参数）。
     *
     * @return 全部记录列表
     */
    public List<RiskAnalysis> listAll() {
        return mapper.selectAll();
    }
}
