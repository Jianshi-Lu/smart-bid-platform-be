package io.gitee.lujianshi.bidwinningbackend.credentialfile.dto;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;

/** 一个资信文件的转换状态。 */
public record FileConvertItem(
        Long fileId,
        String originalFilename,
        ConvertStatus fileStatus,
        String message) {
}
