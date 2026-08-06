package io.gitee.lujianshi.bidwinningbackend.credentialfile.config;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.FileStorageType;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * MinIO 客户端配置。
 *
 * <p>资信文件或知识文件任一存储类型为 {@code MINIO} 时创建全局复用的
 * MinioClient，优先使用资信文件存储配置的连接参数。</p>
 *
 * @since 1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "'${credential-file.storage.type:}' == 'MINIO'"
                + " or '${knowledge-file.storage.type:}' == 'MINIO'")
public class MinioConfiguration {
    /**
     * 根据启用的 MinIO 存储配置创建客户端单例。
     *
     * <p>该过程只构建客户端，不会连接服务器或检查 Bucket。</p>
     *
     * @param credentialProperties 资信文件存储配置
     * @param knowledgeProperties 知识文件存储配置
     * @return 可供 MinIO 存储适配器复用的客户端
     * @throws IllegalArgumentException MinIO 必填配置为空
     */
    @Bean
    public MinioClient minioClient(
            FileStorageProperties credentialProperties,
            KnowledgeFileStorageProperties knowledgeProperties) {
        FileStorageProperties.MinioProperties minio;
        if (credentialProperties.getType() == FileStorageType.MINIO) {
            minio = credentialProperties.getMinio();
        } else {
            minio = knowledgeProperties.getMinio();
        }

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
