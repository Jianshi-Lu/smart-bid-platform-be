package io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto;

import java.util.List;

/**
 * chat-agent 返回的批量知识文件向量化结果。
 *
 * <p>逐文档结果：单个文档失败时 {@code success=false} 并携带
 * {@code message}，不再以整个 HTTP 请求失败表达。</p>
 */
public record ChatAgentVectorizeResponse(List<Document> documents) {

    /** 单个知识文件向量化结果；成功时 {@code chunkCount} 大于 0。 */
    public record Document(
            String documentUuid,
            boolean success,
            Integer chunkCount,
            String message) {
    }
}
