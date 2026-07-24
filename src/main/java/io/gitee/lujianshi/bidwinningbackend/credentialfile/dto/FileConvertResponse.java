package io.gitee.lujianshi.bidwinningbackend.credentialfile.dto;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.BidRecordCredentialConversionStatus;

import java.util.List;

/** 按 bid_record 汇总的资信材料转换响应。 */
public record FileConvertResponse(
        Integer bidRecordId,
        BidRecordCredentialConversionStatus status,
        String message,
        int totalBatchCount,
        int convertedBatchCount,
        int processingBatchCount,
        int failedBatchCount,
        List<BatchConvertResponse> batches) {
}
