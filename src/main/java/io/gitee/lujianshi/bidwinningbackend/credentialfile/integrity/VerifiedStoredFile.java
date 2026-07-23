package io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoredFileMetadata;

/**
 * 已写入持久化存储并通过正文 SHA-256 回读校验的文件。
 *
 * <p>存储属性取自 {@link FileStorage.StoredFile}，SHA-256 取自对已存储正文的实际重算结果，
 * 可作为后续构建 {@code credential_file} 数据记录的可信输入。</p>
 *
 * @param safeOriginalFilename 服务端规范化后的安全原始文件名
 * @param relativePath 存储相对路径或 MinIO object key
 * @param fileSize 存储端确认的文件大小，字节
 * @param contentType 存储端确认的 MIME 类型
 * @param bidRecordId 文件所属的投标记录 ID
 * @param uploadBatchId 上传批次 UUID
 * @param sha256 已存储正文重新计算得到的 SHA-256
 * @param etag 对象存储 ETag；本地存储时为空
 * @since 1.0
 */
public record VerifiedStoredFile(
        String safeOriginalFilename,
        String relativePath,
        long fileSize,
        String contentType,
        Integer bidRecordId,
        String uploadBatchId,
        String sha256,
        String etag) {

    /** 校验经过验证的存储结果必须具备后续落库所需的基础字段。 */
    public VerifiedStoredFile {
        if (safeOriginalFilename == null || safeOriginalFilename.isBlank()) {
            throw new IllegalArgumentException("安全原始文件名不能为空");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("文件相对路径不能为空");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("已存储文件大小不正确");
        }
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new IllegalArgumentException("投标记录 ID 不正确");
        }
        if (uploadBatchId == null || uploadBatchId.isBlank()) {
            throw new IllegalArgumentException("上传批次号不能为空");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 校验和格式不正确");
        }
    }

    /**
     * 使用存储端属性和正文重算校验和创建可信文件结果。
     *
     * @param storedFile 存储端确认的对象属性
     * @param actualSha256 已存储正文重新计算得到的 SHA-256
     * @return 已验证存储文件
     */
    public static VerifiedStoredFile from(
            FileStorage.StoredFile storedFile,
            String safeOriginalFilename,
            String actualSha256) {
        if (storedFile == null) {
            throw new IllegalArgumentException("已存储文件不能为空");
        }
        String bidRecordId = StoredFileMetadata.require(
                storedFile.metadata(), StoredFileMetadata.BID_RECORD_ID);
        String uploadBatchId = StoredFileMetadata.require(
                storedFile.metadata(), StoredFileMetadata.UPLOAD_BATCH_ID);
        return new VerifiedStoredFile(
                safeOriginalFilename,
                storedFile.relativePath(),
                storedFile.fileSize(),
                storedFile.contentType(),
                Integer.valueOf(bidRecordId),
                uploadBatchId,
                actualSha256,
                storedFile.etag());
    }
}
