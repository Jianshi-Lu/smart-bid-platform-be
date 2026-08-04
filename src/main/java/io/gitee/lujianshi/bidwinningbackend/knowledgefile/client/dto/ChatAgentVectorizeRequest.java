package io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto;

import java.util.List;

/** Java 提交给 chat-agent 的批量知识文件向量化请求。 */
public record ChatAgentVectorizeRequest(List<Document> documents) {

    public ChatAgentVectorizeRequest {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    /** 一个 converted Markdown 及其对象存储 metadata。 */
    public record Document(
            String documentUuid,
            String content,
            String originalFilename,
            String originalObjectKey,
            String convertedObjectKey,
            String originalSha256,
            String convertedSha256) {
    }
}
