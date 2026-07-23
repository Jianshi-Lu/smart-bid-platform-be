package io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity;

/**
 * 已存储文件内容完整性校验异常。
 *
 * <p>用于区分普通存储读写失败和已存储内容与预期 SHA-256 不一致。</p>
 *
 * @since 1.0
 */
public class FileIntegrityException extends RuntimeException {

    /**
     * 创建文件完整性校验异常。
     *
     * @param message 不包含文件正文等敏感信息的失败说明
     */
    public FileIntegrityException(String message) {
        super(message);
    }
}
