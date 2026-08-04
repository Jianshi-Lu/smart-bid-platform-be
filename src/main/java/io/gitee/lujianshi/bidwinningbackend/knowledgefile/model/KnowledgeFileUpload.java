package io.gitee.lujianshi.bidwinningbackend.knowledgefile.model;

import java.io.IOException;
import java.io.InputStream;

/** 当前 HTTP 请求中尚未持久化的一个知识文件。 */
public record KnowledgeFileUpload(
        String originalFilename,
        String contentType,
        long fileSize,
        Content content) {

    /** 支持为哈希、上传等步骤重复打开同一份请求内容。 */
    @FunctionalInterface
    public interface Content {
        InputStream openStream() throws IOException;
    }

    public KnowledgeFileUpload {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("知识文件名不能为空");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("知识文件不能为空");
        }
        if (content == null) {
            throw new IllegalArgumentException("知识文件内容不能为空");
        }
    }
}
