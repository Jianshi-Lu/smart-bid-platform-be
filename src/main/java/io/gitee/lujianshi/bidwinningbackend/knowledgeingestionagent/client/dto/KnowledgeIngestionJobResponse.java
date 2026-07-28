package io.gitee.lujianshi.bidwinningbackend.knowledgeingestionagent.client.dto;

import java.util.List;

/** Python返回的政策资讯采集任务状态或最终结果。 */
public record KnowledgeIngestionJobResponse(
        String jobId,
        String status,
        String stage,
        Integer discoveredCount,
        Integer indexedCount,
        Integer skippedCount,
        Integer failedCount,
        List<Document> documents) {

    public KnowledgeIngestionJobResponse {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    /** 当前采集任务处理的一份政策资讯结果。 */
    public record Document(
            String documentId,
            String title,
            String sourceUrl,
            Integer version,
            String originalObjectKey,
            String convertedObjectKey,
            Integer chunkCount,
            String status,
            String errorMessage) {
    }
}
