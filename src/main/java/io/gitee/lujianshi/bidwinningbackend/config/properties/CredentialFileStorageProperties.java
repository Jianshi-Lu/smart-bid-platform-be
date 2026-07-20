package io.gitee.lujianshi.bidwinningbackend.config.properties;

import io.gitee.lujianshi.bidwinningbackend.enums.CredentialFileStorageType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 资信文件存储配置模型。
 *
 * <p>统一绑定本地文件系统与 MinIO 所需参数，具体存储实现根据 {@code type} 选择对应配置。</p>
 *
 * @since 1.0
 */
@ConfigurationProperties(prefix = "credential-file.storage")
@Getter
@Setter
public class CredentialFileStorageProperties {
    private CredentialFileStorageType type = CredentialFileStorageType.LOCAL;

    private LocalProperties local = new LocalProperties();

    private MinioProperties minio = new MinioProperties();


    /** 本地文件系统配置。 */
    @Getter
    @Setter
    public static class LocalProperties {

        private String root;
    }

    /** MinIO 对象存储配置。 */
    @Getter
    @Setter
    public static class MinioProperties {

        private String endpoint;

        private String accessKey;

        private String secretKey;

        private String bucket;

        private boolean createBucketIfAbsent;
    }
}
