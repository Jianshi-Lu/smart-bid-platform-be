package io.gitee.lujianshi.bidwinningbackend.knowledgefile.model;

import java.nio.charset.StandardCharsets;

/** 已完成 original/converted 持久化、可以提交给 chat-agent 的稳定文档。 */
public record PreparedKnowledgeDocument(
        String documentUuid,
        String originalFilename,
        String originalObjectKey,
        String convertedObjectKey,
        String originalSha256,
        String convertedSha256,
        byte[] markdown) {

    public PreparedKnowledgeDocument {
        markdown = markdown == null ? null : markdown.clone();
    }

    @Override
    public byte[] markdown() {
        return markdown == null ? null : markdown.clone();
    }

    /** 将 to-markdown 返回的 UTF-8 文件正文交给 chat-agent JSON 协议。 */
    public String markdownText() {
        return new String(markdown, StandardCharsets.UTF_8);
    }
}
