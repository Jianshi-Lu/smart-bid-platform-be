package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.BidRecordCredentialConversionStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.CredentialMarkdownAggregationService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileConvertService;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.riskanalysis.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.RiskAnalysisMapper;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordVectorIndexService;
import io.gitee.lujianshi.bidwinningbackend.storage.object.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RiskAnalysisServiceTest {

    private static final int BID_RECORD_ID = 18;

    /**
     * 空资信文件列表不是“输入未准备完成”：它应生成明确的基础信息分析输入，
     * 而不是进入转换、汇总或对象存储读取链路。
     */
    @Test
    void shouldPrepareBasicInformationAnalysisWhenCredentialFilesAreAbsent() {
        CredentialFileMapper credentialFileMapper = mock(CredentialFileMapper.class);
        FileConvertService fileConvertService = mock(FileConvertService.class);
        CredentialMarkdownAggregationService aggregationService =
                mock(CredentialMarkdownAggregationService.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        when(credentialFileMapper.selectByBidRecordId(BID_RECORD_ID)).thenReturn(List.of());

        RiskAnalysisService service = service(
                credentialFileMapper,
                fileConvertService,
                aggregationService,
                objectStorage);

        String material = ReflectionTestUtils.invokeMethod(
                service, "prepareCredentialMaterial", BID_RECORD_ID);

        assertTrue(material.contains("当前投标记录未上传资信材料"));
        assertTrue(material.contains("待人工核验"));
        verifyNoInteractions(fileConvertService, aggregationService, objectStorage);
    }

    /** 空材料分支必须继续进入模型调用与结果落库，而不是仅仅停止在输入准备阶段。 */
    @Test
    void shouldCallModelAndPersistAnalysisWhenCredentialFilesAreAbsent() {
        CredentialFileMapper credentialFileMapper = mock(CredentialFileMapper.class);
        FileConvertService fileConvertService = mock(FileConvertService.class);
        CredentialMarkdownAggregationService aggregationService =
                mock(CredentialMarkdownAggregationService.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        RiskAnalysisMapper riskAnalysisMapper = mock(RiskAnalysisMapper.class);
        BidRecordVectorIndexService vectorIndexService =
                mock(BidRecordVectorIndexService.class);
        BidRecordLoader loader = mock(BidRecordLoader.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response =
                mock(ChatClient.CallResponseSpec.class);
        BidRecord record = record();

        when(credentialFileMapper.selectByBidRecordId(BID_RECORD_ID)).thenReturn(List.of());
        when(bidRecordMapper.selectById(BID_RECORD_ID)).thenReturn(record);
        when(bidRecordMapper.claimRiskAnalysis(eq(BID_RECORD_ID), any(LocalDateTime.class)))
                .thenReturn(1);
        when(bidRecordMapper.markRiskAnalysisCompleted(BID_RECORD_ID)).thenReturn(1);
        when(loader.loadDetail(BID_RECORD_ID)).thenReturn(
                "## 投标人基本信息\n"
                        + "- 投标人名称：无资信材料测试企业\n"
                        + "- 报价：1111.00元\n"
                        + "- 联系方式：13800000000\n"
                        + "- 投标次序：第1次\n");
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.chatResponse()).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage(
                        "{\"riskLevel\":\"MEDIUM\","
                                + "\"summary\":\"仅有投标基础信息，资质与履约待核验\","
                                + "\"suggestions\":\"补充资信材料并核验资格和履约记录\"}")))));

        RiskAnalysisService service = service(
                credentialFileMapper,
                fileConvertService,
                aggregationService,
                objectStorage);
        ReflectionTestUtils.setField(service, "chatClient", chatClient);
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "loader", loader);
        ReflectionTestUtils.setField(service, "mapper", riskAnalysisMapper);
        ReflectionTestUtils.setField(service, "bidRecordMapper", bidRecordMapper);
        ReflectionTestUtils.setField(service, "bidRecordVectorIndexService", vectorIndexService);

        RiskAnalysis analysis = service.analyzeRisk(BID_RECORD_ID);

        assertEquals("MEDIUM", analysis.getRiskLevel());
        assertEquals("v2", analysis.getPromptVersion());
        verify(riskAnalysisMapper).insert(analysis);
        verify(bidRecordMapper).markRiskAnalysisCompleted(BID_RECORD_ID);
        verifyNoInteractions(fileConvertService, aggregationService, objectStorage);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(request).user(userPrompt.capture());
        assertTrue(userPrompt.getValue().contains("无资信材料测试企业"));
        assertTrue(userPrompt.getValue().contains("当前投标记录未上传资信材料"));
        assertTrue(userPrompt.getValue().contains("待人工核验"));
    }

    /** 已上传但尚未转完的材料仍必须阻止分析，不能被错误地降级成“无材料”模式。 */
    @Test
    void shouldKeepRejectingExistingCredentialFilesThatAreNotReady() {
        CredentialFileMapper credentialFileMapper = mock(CredentialFileMapper.class);
        FileConvertService fileConvertService = mock(FileConvertService.class);
        CredentialMarkdownAggregationService aggregationService =
                mock(CredentialMarkdownAggregationService.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        CredentialFile pendingFile = new CredentialFile();
        pendingFile.setOriginalFilename("pending.pdf");
        pendingFile.setConvertStatus(ConvertStatus.UPLOADED);

        when(credentialFileMapper.selectByBidRecordId(BID_RECORD_ID))
                .thenReturn(List.of(pendingFile));
        when(fileConvertService.convertAll(BID_RECORD_ID)).thenReturn(
                new FileConvertResponse(
                        BID_RECORD_ID,
                        BidRecordCredentialConversionStatus.PROCESSING,
                        "转换中",
                        1,
                        0,
                        1,
                        0,
                        List.of()));

        RiskAnalysisService service = service(
                credentialFileMapper,
                fileConvertService,
                aggregationService,
                objectStorage);

        AppException exception = assertThrows(
                AppException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service, "prepareCredentialMaterial", BID_RECORD_ID));

        assertEquals(AppErrorCode.RISK_ANALYSIS_INPUT_NOT_READY, exception.getErrorCode());
        assertEquals("PREPARE_CREDENTIAL_FILES", exception.getStage());
        verify(fileConvertService).convertAll(BID_RECORD_ID);
        verifyNoInteractions(aggregationService, objectStorage);
    }

    private RiskAnalysisService service(
            CredentialFileMapper credentialFileMapper,
            FileConvertService fileConvertService,
            CredentialMarkdownAggregationService aggregationService,
            ObjectStorage objectStorage) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiClient(mock(OpenAIClient.class))
                .openAiClientAsync(mock(OpenAIClientAsync.class))
                .build();
        RiskAnalysisService service = new RiskAnalysisService(chatModel);
        ReflectionTestUtils.setField(service, "markdownAggregationService", aggregationService);
        ReflectionTestUtils.setField(service, "fileConvertService", fileConvertService);
        ReflectionTestUtils.setField(service, "credentialFileMapper", credentialFileMapper);
        ReflectionTestUtils.setField(service, "fileStorage", objectStorage);
        return service;
    }

    private BidRecord record() {
        BidRecord record = new BidRecord();
        record.setBidRecordId(BID_RECORD_ID);
        record.setInvitationId(6);
        record.setBidNo(1);
        record.setBidderName("无资信材料测试企业");
        record.setQuotePrice(new BigDecimal("1111.00"));
        record.setBidderContact("13800000000");
        return record;
    }
}
