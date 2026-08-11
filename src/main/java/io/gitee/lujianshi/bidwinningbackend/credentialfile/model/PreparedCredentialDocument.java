package io.gitee.lujianshi.bidwinningbackend.credentialfile.model;

/** 已持久化且回读校验完成、可提交给 vector-service 的资信 Markdown。 */
public record PreparedCredentialDocument(
        String fileUuid,
        String markdownText,
        Long credentialFileId,
        Integer bidRecordId,
        String originalFilename,
        String originalObjectKey,
        String convertedObjectKey,
        String originalSha256,
        String convertedSha256) {
}
