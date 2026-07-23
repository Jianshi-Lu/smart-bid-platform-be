package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.config.storage.CredentialFileStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.MinioException;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 基于 MinIO 的资信文件存储实现。
 *
 * <p>对象 Key 与本地存储相对路径保持一致，上传编排无需感知具体存储介质。</p>
 *
 * @since 1.0
 */
@Service
@ConditionalOnProperty(
        prefix = "credential-file.storage",
        name = "type",
        havingValue = "MINIO")
public class MinioCredentialFileStorage implements CredentialFileStorage {

    private static final String BID_RECORD_ID_METADATA_KEY = "bid-record-id";

    private static final String UPLOAD_BATCH_ID_METADATA_KEY = "upload-batch-id";

    private static final String SHA256_METADATA_KEY = "sha256";

    @Resource
    private MinioClient minioClient;

    @Resource
    private CredentialFileStorageProperties properties;

    /**
     * 将原始文件上传至 MinIO 批次目录。
     *
     * @param request 原始文件保存请求
     * @return 规范化文件名、object key 和文件大小
     * @throws IOException MinIO 上传失败
     */
    @Override
    public StoredFile storeOriginal(StoreOriginalRequest request) throws IOException {
        CredentialFileStorageKey.OriginalFileKey fileKey =
                CredentialFileStorageKey.createOriginalFileKey(
                        request.uploadBatchId(), request.originalFilename());

        // 构造 MinIO 对象上传参数
        PutObjectArgs.Builder argsBuilder = PutObjectArgs.builder()
                .bucket(bucket())
                .object(fileKey.relativePath())
                .stream(request.input(), request.contentLength(), -1L)
                .userMetadata(Map.of(
                        BID_RECORD_ID_METADATA_KEY, request.bidRecordId().toString(),
                        UPLOAD_BATCH_ID_METADATA_KEY, request.uploadBatchId(),
                        SHA256_METADATA_KEY, request.sha256()));
        if (request.contentType() != null && !request.contentType().isBlank()) {
            argsBuilder.contentType(request.contentType());
        }

        ObjectWriteResponse writeResponse;
        try {
            // MinIO 对象上传入口
            writeResponse = minioClient.putObject(argsBuilder.build());
        } catch (MinioException e) {
            throw new IOException("MinIO 原始文件上传失败: " + fileKey.relativePath(), e);
        }

        String uploadedVersionId = writeResponse == null ? null : writeResponse.versionId();
        try {
            StatObjectResponse statResponse = statStoredObject(
                    fileKey.relativePath(), uploadedVersionId);
            validateStoredObject(request, writeResponse, statResponse, fileKey.relativePath());
            return new StoredFile(
                    fileKey.safeOriginalFilename(),
                    fileKey.relativePath(),
                    statResponse.size(),
                    statResponse.contentType(),
                    Integer.valueOf(statResponse.userMetadata().getFirst(BID_RECORD_ID_METADATA_KEY)),
                    statResponse.userMetadata().getFirst(UPLOAD_BATCH_ID_METADATA_KEY),
                    statResponse.userMetadata().getFirst(SHA256_METADATA_KEY),
                    statResponse.etag(),
                    statResponse.versionId());
        } catch (MinioException | IOException | RuntimeException e) {
            IOException failure = e instanceof IOException ioException
                    ? ioException
                    : new IOException("MinIO 原始文件上传后校验失败: " + fileKey.relativePath(), e);
            cleanupUploadedObject(fileKey.relativePath(), uploadedVersionId, failure);
            throw failure;
        }
    }

    /**
     * 回读刚上传对象的属性和用户 metadata。
     *
     * @param objectKey MinIO object key
     * @param versionId 刚上传对象的版本 ID；未启用版本控制时为空
     * @return MinIO 对象属性
     * @throws MinioException MinIO 对象查询失败
     */
    private StatObjectResponse statStoredObject(String objectKey, String versionId)
            throws MinioException {
        StatObjectArgs.Builder builder = StatObjectArgs.builder()
                .bucket(bucket())
                .object(objectKey);
        if (hasText(versionId)) {
            builder.versionId(versionId);
        }
        return minioClient.statObject(builder.build());
    }

    /**
     * 校验 MinIO 回读结果与上传请求及写入响应一致。
     *
     * @param request 原始文件保存请求
     * @param writeResponse putObject 写入响应
     * @param statResponse statObject 回读响应
     * @param objectKey MinIO object key
     * @throws IOException 文件大小、metadata、ETag 或版本 ID 不一致
     */
    private void validateStoredObject(
            StoreOriginalRequest request,
            ObjectWriteResponse writeResponse,
            StatObjectResponse statResponse,
            String objectKey) throws IOException {
        if (writeResponse == null) {
            throw new IOException("MinIO 上传未返回对象写入结果: " + objectKey);
        }
        if (statResponse == null) {
            throw new IOException("MinIO 上传后未返回对象属性: " + objectKey);
        }
        if (statResponse.size() != request.contentLength()) {
            throw new IOException("MinIO 对象大小与上传文件不一致: " + objectKey);
        }

        requireMetadata(
                statResponse, BID_RECORD_ID_METADATA_KEY,
                request.bidRecordId().toString(), objectKey);
        requireMetadata(
                statResponse, UPLOAD_BATCH_ID_METADATA_KEY,
                request.uploadBatchId(), objectKey);
        requireMetadata(
                statResponse, SHA256_METADATA_KEY,
                request.sha256(), objectKey);

        if (!hasText(statResponse.etag())) {
            throw new IOException("MinIO 对象 ETag 为空: " + objectKey);
        }
        if (hasText(writeResponse.etag())
                && !writeResponse.etag().equals(statResponse.etag())) {
            throw new IOException("MinIO 对象 ETag 与上传结果不一致: " + objectKey);
        }
        if (hasText(writeResponse.versionId())
                && !writeResponse.versionId().equals(statResponse.versionId())) {
            throw new IOException("MinIO 对象版本与上传结果不一致: " + objectKey);
        }
    }

    /** 校验指定用户 metadata 存在且与期望值一致。 */
    private void requireMetadata(
            StatObjectResponse statResponse,
            String metadataKey,
            String expectedValue,
            String objectKey) throws IOException {
        String actualValue = statResponse.userMetadata().getFirst(metadataKey);
        if (!expectedValue.equals(actualValue)) {
            throw new IOException("MinIO 对象 metadata 不一致: "
                    + metadataKey + ", object=" + objectKey);
        }
    }

    /**
     * 上传后校验失败时尽力删除当前对象，不覆盖原始失败原因。
     */
    private void cleanupUploadedObject(
            String objectKey,
            String versionId,
            IOException originalFailure) {
        RemoveObjectArgs.Builder builder = RemoveObjectArgs.builder()
                .bucket(bucket())
                .object(objectKey);
        if (hasText(versionId)) {
            builder.versionId(versionId);
        }
        try {
            minioClient.removeObject(builder.build());
        } catch (MinioException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    /** 判断字符串是否包含非空白内容。 */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 根据 object key 打开 MinIO 对象流。
     *
     * @param relativePath MinIO object key
     * @return 对象输入流，由调用方关闭
     * @throws IOException MinIO 对象读取失败
     */
    @Override
    public InputStream open(String relativePath) throws IOException {
        String objectKey = CredentialFileStorageKey.normalizeRelativePath(relativePath);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket())
                            .object(objectKey)
                            .build());
        } catch (MinioException e) {
            throw new IOException("MinIO 文件读取失败: " + objectKey, e);
        }
    }

    /**
     * 根据 object key 删除 MinIO 对象。
     *
     * @param relativePath MinIO object key
     * @throws IOException MinIO 对象删除失败
     */
    @Override
    public void delete(String relativePath) throws IOException {
        String objectKey = CredentialFileStorageKey.normalizeRelativePath(relativePath);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket())
                            .object(objectKey)
                            .build());
        } catch (MinioException e) {
            throw new IOException("MinIO 文件删除失败: " + objectKey, e);
        }
    }

    /** 返回配置中的资信文件 Bucket。 */
    private String bucket() {
        return properties.getMinio().getBucket();
    }
}
