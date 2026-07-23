package io.gitee.lujianshi.bidwinningbackend.credentialfile.intake;

/**
 * 已进入后端请求流程、尚未写入持久化存储的单个文件条目。
 *
 * @param originalFilename 客户端提供的原始文件名
 * @param contentType 文件 MIME 类型，未知时可以为空
 * @param fileSize 文件大小，字节
 * @param content 支持重复打开的文件内容入口
 * @since 1.0
 */
public record PendingUploadItem(
        String originalFilename,
        String contentType,
        long fileSize,
        ItemContent content) {

    /** 校验单个待上传条目的基础边界。 */
    public PendingUploadItem {
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
