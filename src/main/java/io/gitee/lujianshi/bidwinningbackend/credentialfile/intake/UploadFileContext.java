package io.gitee.lujianshi.bidwinningbackend.credentialfile.intake;

import java.util.UUID;

/**
 * 单个资信文件单元的有效上传上下文。
 *
 * @param bidRecordId 文件所属的投标记录 ID
 * @param bidderAccountId 文件所属投标方的账号 ID（路径顶层归属与溯源根）
 * @param username 投标方登录用户名（对象存储路径顶层归属）
 * @param fileUuid 文件单元 UUID，作为对象目录与 {@code credential_file.file_uuid} 兼容值
 * @since 1.0
 */
public record UploadFileContext(
        Integer bidRecordId,
        Long bidderAccountId,
        String username,
        String fileUuid) {

    /** 校验上下文字段，并将文件单元 UUID 规范化为标准 UUID 字符串。 */
    public UploadFileContext {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new IllegalArgumentException("投标记录 ID 不正确");
        }
        if (bidderAccountId == null || bidderAccountId <= 0) {
            throw new IllegalArgumentException("投标方账号 ID 不正确");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("投标方登录用户名不能为空");
        }
        try {
            fileUuid = UUID.fromString(fileUuid).toString();
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new IllegalArgumentException("文件单元 UUID 格式不正确", e);
        }
    }
}
