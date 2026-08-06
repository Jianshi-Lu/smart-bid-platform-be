package io.gitee.lujianshi.bidwinningbackend.credentialfile.config;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.FileStorageType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识文件（RAG 入库）存储配置。
 *
 * <p>与 {@link FileStorageProperties} 形状一致，但前缀独立为
 * {@code knowledge-file.storage}，使资信材料与 RAG 知识文件可以写入不同的
 * MinIO Bucket 或本地根目录。</p>
 *
 * @since 1.0
 */
@ConfigurationProperties(prefix = "knowledge-file.storage")
@Getter
@Setter
public class KnowledgeFileStorageProperties {

    private FileStorageType type = FileStorageType.LOCAL;

    private FileStorageProperties.LocalProperties local =
            new FileStorageProperties.LocalProperties();

    private FileStorageProperties.MinioProperties minio =
            new FileStorageProperties.MinioProperties();
}
