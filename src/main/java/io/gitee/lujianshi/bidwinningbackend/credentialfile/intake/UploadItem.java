package io.gitee.lujianshi.bidwinningbackend.credentialfile.intake;

import java.io.IOException;
import java.io.InputStream;

/**
 * 已进入后端请求流程、尚未写入持久化存储的单个文件条目。
 *
 * @param originalFilename 客户端提供的原始文件名
 * @param contentType 文件 MIME 类型，未知时可以为空
 * @param fileSize 文件大小，字节
 * @param content 支持重复打开的文件内容入口
 * @since 1.0
 */
public record UploadItem(
        String originalFilename,
        String contentType,
        long fileSize,
        Content content) {

    /**
     * 单个上传文件的内容入口。
     *
     * <p>每次调用都必须返回一个新的、可独立读取和关闭的输入流，
     * 以支持校验和计算与文件持久化分别读取同一份上传内容。</p>
     */
    @FunctionalInterface
    public interface Content {

        /** 打开一条新的上传内容输入流。 */
        InputStream openStream() throws IOException;
    }

    /** 紧凑构造器：校验单个待上传条目的基础边界。 */
    public UploadItem {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("原始文件名不能为空");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (content == null) {
            throw new IllegalArgumentException("上传文件内容不能为空");
        }
    }
}
