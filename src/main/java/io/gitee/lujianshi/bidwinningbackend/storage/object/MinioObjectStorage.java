package io.gitee.lujianshi.bidwinningbackend.storage.object;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.MinioException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 MinIO 的文件存储实现。
 *
 * <p>对象 Key 与本地存储逻辑路径保持一致，上层处理流程无需感知具体存储介质；
 * 使用的 Bucket 在构造时由调用方指定，资信材料与知识文件因此可以分开落桶。</p>
 *
 * @since 1.0
 */
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient minioClient;

    private final String bucket;

    /**
     * 创建 MinIO 存储实现。
     *
     * @param minioClient 共享的 MinIO 客户端
     * @param bucket 本存储实例写入的 Bucket
     * @throws IllegalArgumentException 客户端或 Bucket 为空时抛出
     */
    public MinioObjectStorage(MinioClient minioClient, String bucket) {
        if (minioClient == null) {
            throw new IllegalArgumentException("MinIO 客户端不能为空");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("MinIO bucket 不能为空");
        }
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    /**
     * 将文件上传至调用方指定的 MinIO object key 并回读对象属性。
     *
     * @param request 通用文件保存请求
     * @return statObject 确认后的文件结果
     * @throws IOException MinIO 上传或对象属性校验失败
     */
    @Override
    public StoredFile store(StoreRequest request) throws IOException {
        String objectKey = ObjectKey.normalize(request.relativePath());

        PutObjectArgs.Builder argsBuilder = PutObjectArgs.builder()
                .bucket(bucket())
                .object(objectKey)
                .stream(request.input(), request.contentLength(), -1L)
                .userMetadata(request.metadata());
        if (request.contentType() != null && !request.contentType().isBlank()) {
            argsBuilder.contentType(request.contentType());
        }

        ObjectWriteResponse writeResponse;
        try {
            writeResponse = minioClient.putObject(argsBuilder.build());
        } catch (MinioException e) {
            throw new IOException("MinIO 文件上传失败: " + objectKey, e);
        }

        try {
            StatObjectResponse statResponse = statStoredObject(objectKey);
            validateStoredObject(request, writeResponse, statResponse, objectKey);
            return new StoredFile(
                    objectKey,
                    statResponse.size(),
                    statResponse.contentType(),
                    readMetadata(statResponse, request.metadata()),
                    statResponse.etag());
        } catch (MinioException | IOException | RuntimeException e) {
            IOException failure = e instanceof IOException ioException
                    ? ioException
                    : new IOException(
                            "MinIO 文件上传后校验失败: " + objectKey, e);
            cleanupUploadedObject(objectKey, failure);
            throw failure;
        }
    }

    /** 回读刚上传对象的系统属性和调用方自定义的文件 metadata。 */
    private StatObjectResponse statStoredObject(String objectKey) throws MinioException {
        return minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucket())
                        .object(objectKey)
                        .build());
    }

    /** 校验 MinIO 回读结果与上传请求及写入响应一致。 */
    private void validateStoredObject(
            StoreRequest request,
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

        for (Map.Entry<String, String> metadata : request.metadata().entrySet()) {
            requireMetadata(
                    statResponse,
                    metadata.getKey(),
                    metadata.getValue(),
                    objectKey);
        }

        if (!hasText(statResponse.etag())) {
            throw new IOException("MinIO 对象 ETag 为空: " + objectKey);
        }
        if (hasText(writeResponse.etag())
                && !writeResponse.etag().equals(statResponse.etag())) {
            throw new IOException("MinIO 对象 ETag 与上传结果不一致: " + objectKey);
        }
    }

    /** 将 MinIO 回读的调用方自定义文件 metadata 转换成通用不可变键值数据。 */
    private Map<String, String> readMetadata(
            StatObjectResponse statResponse,
            Map<String, String> requestedMetadata) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String key : requestedMetadata.keySet()) {
            metadata.put(key, statResponse.userMetadata().getFirst(key));
        }
        return Map.copyOf(metadata);
    }

    /** 校验指定的调用方自定义文件 metadata 存在且与期望值一致。 */
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

    /** 上传后属性校验失败时尽力删除当前对象，不覆盖原始失败原因。 */
    private void cleanupUploadedObject(
            String objectKey,
            IOException originalFailure) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket())
                            .object(objectKey)
                            .build());
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
        return open(new OpenRequest(relativePath, null));
    }

    /**
     * 按 object key 和可选 ETag 条件打开 MinIO 对象流。
     *
     * @param request 文件读取请求
     * @return 对象输入流，由调用方关闭
     * @throws IOException MinIO 对象读取失败或 ETag 条件不匹配
     */
    @Override
    public InputStream open(OpenRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("文件读取请求不能为空");
        }
        String objectKey = ObjectKey.normalize(request.relativePath());
        GetObjectArgs.Builder builder = GetObjectArgs.builder()
                .bucket(bucket())
                .object(objectKey);
        if (hasText(request.matchEtag())) {
            builder.matchETag(request.matchEtag());
        }
        try {
            return minioClient.getObject(builder.build());
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
        String objectKey = ObjectKey.normalize(relativePath);
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

    /** 返回本存储实例绑定的 Bucket。 */
    private String bucket() {
        return bucket;
    }
}
