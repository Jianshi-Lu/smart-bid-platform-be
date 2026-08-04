package io.gitee.lujianshi.bidwinningbackend.knowledgefile.dto;

import java.util.List;

/** 前端一次多文件上传、转换和向量化的同步成功结果。 */
public record KnowledgeFileUploadResponse(List<Document> documents) {

    public KnowledgeFileUploadResponse {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public record Document(
            String documentUuid,
            String originalFilename,
            String originalObjectKey,
            String convertedObjectKey,
            String originalSha256,
            String convertedSha256,
            int chunkCount) {
    }
}
