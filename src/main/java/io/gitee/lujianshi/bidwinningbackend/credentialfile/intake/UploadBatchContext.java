package io.gitee.lujianshi.bidwinningbackend.credentialfile.intake;

import java.util.UUID;

/**
 * 已完成投标记录和上传批次号解析的有效批次上下文。
 *
 * @param bidRecordId 文件所属的投标记录 ID
 * @param uploadBatchId 标准 UUID 格式的上传批次号
 * @since 1.0
 */
public record UploadBatchContext(
        Integer bidRecordId,
        String uploadBatchId) {

    /** 校验上下文字段，并将批次号规范化为标准 UUID 字符串。 */
    public UploadBatchContext {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new IllegalArgumentException("投标记录 ID 不正确");
        }
        try {
            uploadBatchId = UUID.fromString(uploadBatchId).toString();
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new IllegalArgumentException("上传批次号格式不正确", e);
        }
    }
}
