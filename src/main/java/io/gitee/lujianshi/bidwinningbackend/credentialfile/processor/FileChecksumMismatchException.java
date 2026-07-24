package io.gitee.lujianshi.bidwinningbackend.credentialfile.processor;

/** 文件在存储前后的校验和不一致。 */
public class FileChecksumMismatchException extends RuntimeException {

    /** 使用不包含文件正文或摘要值的内部错误消息创建异常。 */
    public FileChecksumMismatchException(String message) {
        super(message);
    }
}
