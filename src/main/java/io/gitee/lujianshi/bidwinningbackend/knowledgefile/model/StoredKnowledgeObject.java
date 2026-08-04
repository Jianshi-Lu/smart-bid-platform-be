package io.gitee.lujianshi.bidwinningbackend.knowledgefile.model;

/** 一个已经写入对象存储并通过正文回读校验的知识对象。 */
public record StoredKnowledgeObject(
        String objectKey,
        long fileSize,
        String contentType,
        String sha256,
        String etag) {
}
