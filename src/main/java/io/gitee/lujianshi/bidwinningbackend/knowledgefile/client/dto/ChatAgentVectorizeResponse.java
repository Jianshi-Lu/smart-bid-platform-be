package io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto;

import java.util.List;

/** chat-agent 返回的批量知识文件向量化结果。 */
public record ChatAgentVectorizeResponse(List<Document> documents) {

    /** 单个知识文件实际产生的 chunk 数量。 */
    public record Document(
            String documentUuid,
            int chunkCount) {
    }
}
