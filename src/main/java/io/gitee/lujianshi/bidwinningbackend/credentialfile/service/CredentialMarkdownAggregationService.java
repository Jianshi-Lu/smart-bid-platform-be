package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.config.RiskAnalysisProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidderAccountMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 投标记录级资信 Markdown 汇总服务。
 *
 * <p>该服务只处理已转换文件的状态校验、读取、顺序拼接和派生文件持久化；它不调用大模型。
 * 汇总文件使用固定 object key，每次风险分析前重新生成并覆盖。</p>
 */
@Service
public class CredentialMarkdownAggregationService {

    private static final String MARKDOWN_CONTENT_TYPE = "text/markdown; charset=UTF-8";

    @Resource
    private CredentialFileMapper fileMapper;

    @Resource
    private FileStorage fileStorage;

    @Resource
    private RiskAnalysisProperties riskAnalysisProperties;

    @Resource
    private BidRecordMapper bidRecordMapper;

    @Resource
    private BidderAccountMapper bidderAccountMapper;

    /**
     * 汇总指定投标记录下的全部已转换 Markdown，并写入投标记录级路径。
     *
     * @param bidRecordId 投标记录 ID
     * @return 汇总 Markdown 的逻辑存储路径或 MinIO object key
     */
    public String aggregate(Integer bidRecordId) {
        validateBidRecordId(bidRecordId);

        BidRecord record = bidRecordMapper.selectById(bidRecordId);
        if (record == null || record.getBidderAccountId() == null) {
            throw inputNotReady("投标记录不存在或未绑定投标方账号，不能进行风险分析");
        }
        BidderAccount bidderAccount =
                bidderAccountMapper.selectById(record.getBidderAccountId());
        if (bidderAccount == null) {
            throw inputNotReady("投标记录绑定的投标方账号不存在，不能进行风险分析");
        }
        List<CredentialFile> orderedFiles = loadReadyFiles(bidRecordId);
        Path temporaryPath = null;
        try {
            temporaryPath = Files.createTempFile("bid-record-" + bidRecordId + "-", ".md");
            long characterCount = writeMergedMarkdown(temporaryPath, bidRecordId, orderedFiles);
            long contentLength = Files.size(temporaryPath);
            String mergedPath = StoragePath.createMergedMarkdownPath(
                    bidRecordId, bidderAccount.getUsername());

            try (InputStream input = Files.newInputStream(temporaryPath)) {
                fileStorage.store(new FileStorage.StoreRequest(
                        mergedPath,
                        MARKDOWN_CONTENT_TYPE,
                        contentLength,
                        Map.of(
                                "bid-record-id", bidRecordId.toString(),
                                "bidder-account-id", record.getBidderAccountId().toString(),
                                "source-file-count", Integer.toString(orderedFiles.size()),
                                "character-count", Long.toString(characterCount)),
                        input));
            }
            return mergedPath;
        } catch (AppException e) {
            throw e;
        } catch (IOException e) {
            throw markdownAggregationFailed("资信材料 Markdown 汇总失败", e);
        } catch (RuntimeException e) {
            throw markdownAggregationFailed("资信材料 Markdown 汇总失败", e);
        } finally {
            if (temporaryPath != null) {
                try {
                    Files.deleteIfExists(temporaryPath);
                } catch (IOException ignored) {
                    // 汇总失败的主异常优先返回；临时文件由系统临时目录后续清理。
                }
            }
        }
    }

    /** 校验文件状态，并按数据库文件顺序组织可汇总文件。 */
    private List<CredentialFile> loadReadyFiles(Integer bidRecordId) {
        List<CredentialFile> files = fileMapper.selectByBidRecordId(bidRecordId);
        if (files == null || files.isEmpty()) {
            throw inputNotReady("该投标记录暂无资信材料，不能进行风险分析");
        }

        List<CredentialFile> orderedFiles = new ArrayList<>();
        for (CredentialFile file : files) {
            if (file == null
                    || file.getConvertStatus() != ConvertStatus.CONVERTED
                    || file.getMarkdownFilePath() == null
                    || file.getMarkdownFilePath().isBlank()) {
                String filename = file == null ? "未知" : file.getOriginalFilename();
                throw inputNotReady(
                        "资信材料尚未全部转换完成，文件状态不满足要求: " + filename);
            }
            orderedFiles.add(file);
        }

        if (orderedFiles.isEmpty()) {
            throw inputNotReady("该投标记录没有可汇总的资信材料");
        }
        return orderedFiles;
    }

    /** 将各个 Markdown 按批次和文件边界写入临时文件。 */
    private long writeMergedMarkdown(
            Path temporaryPath,
            Integer bidRecordId,
            List<CredentialFile> files) throws IOException {
        long characterCount = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(
                temporaryPath, StandardCharsets.UTF_8)) {
            characterCount = appendText(writer,
                    "# 投标记录资信材料汇总\n\n投标记录 ID："
                            + bidRecordId + "\n\n", characterCount);

            for (CredentialFile file : files) {
                characterCount = appendText(writer,
                        "### 文件：" + file.getOriginalFilename() + "\n\n---\n\n",
                        characterCount);
                characterCount = appendMarkdownFile(writer, file, characterCount);
                characterCount = appendText(writer, "\n\n---\n\n", characterCount);
            }
        }
        return characterCount;
    }

    /** 将一个 converted Markdown 对象以 UTF-8 文本追加到汇总文件。 */
    private long appendMarkdownFile(
            BufferedWriter writer,
            CredentialFile file,
            long characterCount) throws IOException {
        try (InputStream input = fileStorage.open(file.getMarkdownFilePath());
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                characterCount = appendText(writer, new String(buffer, 0, read), characterCount);
            }
        }
        return characterCount;
    }

    /** 追加文本并执行配置的汇总长度限制。 */
    private long appendText(
            BufferedWriter writer,
            String text,
            long currentCharacterCount) throws IOException {
        long nextCharacterCount = currentCharacterCount + text.length();
        int maxCharacters = riskAnalysisProperties.getMaxMergedMarkdownChars();
        if (maxCharacters <= 0) {
            throw new AppException(
                    AppErrorCode.SYSTEM_INTERNAL_ERROR,
                    "risk-analysis",
                    "ANALYZE_RISK",
                    "VALIDATE_MERGED_MARKDOWN_CONFIG",
                    "风险分析 Markdown 长度配置必须大于 0");
        }
        if (nextCharacterCount > maxCharacters) {
            throw inputNotReady(
                    "汇总后的资信材料超过风险分析上下文限制（" + maxCharacters + " 字符）");
        }
        writer.write(text);
        return nextCharacterCount;
    }

    private void validateBidRecordId(Integer bidRecordId) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new AppException(
                    AppErrorCode.COMMON_INVALID_ARGUMENT,
                    "risk-analysis",
                    "ANALYZE_RISK",
                    "VALIDATE_BID_RECORD",
                    "投标记录 ID 不正确");
        }
    }

    /** 创建风险分析输入未准备完成异常。 */
    private AppException inputNotReady(String message) {
        return new AppException(
                AppErrorCode.RISK_ANALYSIS_INPUT_NOT_READY,
                "risk-analysis",
                "ANALYZE_RISK",
                "VALIDATE_INPUT",
                message);
    }

    /** 创建 Markdown 汇总失败异常，并保留底层原因供日志排查。 */
    private AppException markdownAggregationFailed(
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.RISK_ANALYSIS_MARKDOWN_AGGREGATION_FAILED,
                "risk-analysis",
                "ANALYZE_RISK",
                "AGGREGATE_MARKDOWN",
                message,
                cause);
    }

}
