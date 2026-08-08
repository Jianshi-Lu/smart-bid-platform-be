package io.gitee.lujianshi.bidwinningbackend.knowledgefile.dto;

import java.util.List;

/**
 * 前端一次多文件上传、转换和向量化的同步结果。
 *
 * <p>逐文件返回状态：单个文件失败不再连坐整批，失败原因写入
 * {@code errorMessage}，调用方可以按 documentUuid 后续重试。</p>
 */
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
            Integer chunkCount,
            String originalStorageStatus,
            String convertStatus,
            String indexStatus,
            String errorMessage) {
    }
}
