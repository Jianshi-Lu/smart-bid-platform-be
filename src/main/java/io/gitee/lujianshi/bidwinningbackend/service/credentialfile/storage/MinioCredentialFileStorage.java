package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.config.storage.CredentialFileStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

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

        PutObjectArgs.Builder argsBuilder = PutObjectArgs.builder()
                .bucket(bucket())
                .object(fileKey.relativePath())
                .stream(request.input(), request.contentLength(), -1L);
        if (request.contentType() != null && !request.contentType().isBlank()) {
            argsBuilder.contentType(request.contentType());
        }

        try {
            minioClient.putObject(argsBuilder.build());
            return new StoredFile(
                    fileKey.safeOriginalFilename(),
                    fileKey.relativePath(),
                    request.contentLength());
        } catch (MinioException e) {
            throw new IOException("MinIO 原始文件上传失败: " + fileKey.relativePath(), e);
        }
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
