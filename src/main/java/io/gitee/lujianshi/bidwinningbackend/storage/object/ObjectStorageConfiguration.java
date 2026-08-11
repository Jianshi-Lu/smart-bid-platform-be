package io.gitee.lujianshi.bidwinningbackend.storage.object;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.KnowledgeFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.FileStorageType;
import io.minio.MinioClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 文件存储 Bean 装配。
 *
 * <p>资信材料与知识文件各自独立绑定存储配置：{@code credentialFileStorage}
 * 使用 {@code credential-file.storage.*}，{@code knowledgeFileStorage} 使用
 * {@code knowledge-file.storage.*}。两条链路因此可以落在不同的 MinIO Bucket，
 * 但共享同一个 {@link MinioClient}。</p>
 *
 * @since 1.0
 */
@Configuration(proxyBeanMethods = false)
public class ObjectStorageConfiguration {

    /**
     * 资信文件存储，业务默认注入此 Bean。
     */
    @Bean
    @Primary
    public ObjectStorage credentialFileStorage(
            FileStorageProperties properties,
            ObjectProvider<MinioClient> minioClientProvider) {
        return createStorage(
                properties.getType(),
                properties.getLocal().getRoot(),
                properties.getMinio().getBucket(),
                minioClientProvider);
    }

    /**
     * 知识文件（RAG）存储，由知识文件模块按名称注入。
     */
    @Bean
    public ObjectStorage knowledgeFileStorage(
            KnowledgeFileStorageProperties properties,
            ObjectProvider<MinioClient> minioClientProvider) {
        return createStorage(
                properties.getType(),
                properties.getLocal().getRoot(),
                properties.getMinio().getBucket(),
                minioClientProvider);
    }

    private ObjectStorage createStorage(
            FileStorageType type,
            String localRoot,
            String minioBucket,
            ObjectProvider<MinioClient> minioClientProvider) {
        if (type == FileStorageType.MINIO) {
            MinioClient minioClient = minioClientProvider.getIfAvailable();
            if (minioClient == null) {
                throw new IllegalStateException(
                        "存储类型为 MINIO 但 MinioClient 未创建，请检查 MinIO 配置");
            }
            return new MinioObjectStorage(minioClient, minioBucket);
        }
        return new LocalObjectStorage(localRoot);
    }
}
