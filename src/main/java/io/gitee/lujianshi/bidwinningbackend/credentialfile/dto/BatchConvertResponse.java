package io.gitee.lujianshi.bidwinningbackend.credentialfile.dto;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;

/** 一个资信文件批次的转换状态。 */
public record BatchConvertResponse(
        String uploadBatchId,
        Integer batchNo,
        ConvertStatus batchStatus,
        Integer fileCount,
        Integer convertedFileCount,
        String message) {
}
