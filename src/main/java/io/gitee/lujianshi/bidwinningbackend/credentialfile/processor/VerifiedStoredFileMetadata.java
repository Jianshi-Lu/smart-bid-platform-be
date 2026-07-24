package io.gitee.lujianshi.bidwinningbackend.credentialfile.processor;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;

/**
 * 已完成持久化和正文回读校验的文件元数据，可以安全写入 {@code credential_file}。
 *
 * @param safeOriginalFilename 服务端规范化后的安全原始文件名
 * @param relativePath 存储相对路径或 MinIO object key
 * @param fileSize 存储端确认的文件大小，字节
 * @param contentType 存储端确认的 MIME 类型
 * @param sha256 上传前与存储后回读结果一致的 SHA-256
 */
public record VerifiedStoredFileMetadata(
        String safeOriginalFilename,
        String relativePath,
        long fileSize,
        String contentType,
        String sha256) {

    /** 校验后续落库所依赖的基础字段。 */
    public VerifiedStoredFileMetadata {
        if (safeOriginalFilename == null || safeOriginalFilename.isBlank()) {
            throw new IllegalArgumentException("安全原始文件名不能为空");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("文件相对路径不能为空");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("已存储文件大小不正确");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 校验和格式不正确");
        }
    }

    /** 从已确认的存储结果中提取可落库的文件元数据。 */
    public static VerifiedStoredFileMetadata extractMetadata(
            FileStorage.StoredFile storedFile,
            String safeOriginalFilename,
            String sha256) {
        if (storedFile == null) {
            throw new IllegalArgumentException("已存储文件不能为空");
        }
        return new VerifiedStoredFileMetadata(
                safeOriginalFilename,
                storedFile.relativePath(),
                storedFile.fileSize(),
                storedFile.contentType(),
                sha256);
    }
}
