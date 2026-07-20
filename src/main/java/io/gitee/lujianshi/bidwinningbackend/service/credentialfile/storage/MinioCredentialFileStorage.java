package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 基于 MinIO 的资信文件存储适配器脚手架。
 *
 * <p>当前仅建立 {@code MINIO} 模式的条件装配入口，具体对象上传、读取和删除将在后续阶段实现。</p>
 *
 * @since 1.0
 */
@Service
@ConditionalOnProperty(
        prefix = "credential-file.storage",
        name = "type",
        havingValue = "MINIO")
public class MinioCredentialFileStorage implements CredentialFileStorage {

    @Override
    public StoredFile storeOriginal(String uploadBatchId, String originalFilename, InputStream input) throws IOException {
        // TODO: 使用 MinIO SDK 将原始文件上传到批次 original object key。
        return null;
    }

    @Override
    public InputStream open(String relativePath) throws IOException {
        // TODO: 使用 MinIO SDK 打开 object key 对应的对象流。
        return null;
    }

    @Override
    public void delete(String relativePath) throws IOException {
        // TODO: 使用 MinIO SDK 删除 object key 对应的对象。
    }
}
