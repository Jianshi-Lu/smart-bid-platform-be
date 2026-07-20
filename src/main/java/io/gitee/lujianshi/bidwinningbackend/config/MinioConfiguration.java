package io.gitee.lujianshi.bidwinningbackend.config;

import io.gitee.lujianshi.bidwinningbackend.config.properties.CredentialFileStorageProperties;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * MinIO 客户端配置。
 *
 * <p>仅在资信文件存储类型为 {@code MINIO} 时创建全局复用的 MinioClient。</p>
 *
 * @since 1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "credential-file.storage",
        name = "type",
        havingValue = "MINIO")
public class MinioConfiguration {
    /**
     * 根据资信文件 MinIO 配置创建客户端单例。
     *
     * <p>该过程只构建客户端，不会连接服务器或检查 Bucket。</p>
     *
     * @param properties 资信文件存储配置
     * @return 可供 MinIO 存储适配器复用的客户端
     * @throws IllegalArgumentException MinIO 必填配置为空
     */
    @Bean
    public MinioClient minioClient(CredentialFileStorageProperties properties) {
        CredentialFileStorageProperties.MinioProperties minio = properties.getMinio();

        Assert.hasText(minio.getEndpoint(), "MinIO endpoint 不能为空");
        Assert.hasText(minio.getAccessKey(), "MinIO access key 不能为空");
        Assert.hasText(minio.getSecretKey(), "MinIO secret key 不能为空");
        Assert.hasText(minio.getBucket(), "MinIO bucket 不能为空");

        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(
                        minio.getAccessKey(),
                        minio.getSecretKey())
                .build();
    }
}
