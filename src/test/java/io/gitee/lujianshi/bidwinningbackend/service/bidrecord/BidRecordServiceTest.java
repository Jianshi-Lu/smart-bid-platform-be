package io.gitee.lujianshi.bidwinningbackend.service.bidrecord;

import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.RiskAnalysisMapper;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidRecordServiceTest {

    @Test
    void shouldDeleteRiskAnalysesWithTheirBidRecord() {
        BidRecordService service = new BidRecordService();
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        RiskAnalysisMapper riskAnalysisMapper = mock(RiskAnalysisMapper.class);
        OperationHistoryService historyService =
                mock(OperationHistoryService.class);
        BidRecord record = new BidRecord();
        record.setBidRecordId(13);
        when(bidRecordMapper.selectById(13)).thenReturn(record);
        when(bidRecordMapper.deleteById(13)).thenReturn(1);
        ReflectionTestUtils.setField(service, "mapper", bidRecordMapper);
        ReflectionTestUtils.setField(
                service, "riskAnalysisMapper", riskAnalysisMapper);
        ReflectionTestUtils.setField(
                service, "operationHistoryService", historyService);

        assertEquals(1, service.deleteWithRiskAnalyses(13));

        verify(riskAnalysisMapper).deleteByBidRecordId(13);
    }
}
