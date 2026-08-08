package io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto;

/**
 * chat-agent 返回的单个知识文件向量状态。
 *
 * <p>{@code chunkCount} 为当前 Chroma 中该 documentUuid 实际存在的 chunk 数，
 * 不存在时返回 0。Java 用于重试前对账与重启后的状态校验。</p>
 */
public record ChatAgentVectorStatusResponse(
        String documentUuid,
        int chunkCount) {
}
