package io.gitee.lujianshi.bidwinningbackend.config;

import io.gitee.lujianshi.bidwinningbackend.config.properties.CredentialFileStorageProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class MinioConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MinioTestConfiguration.class);

    @Test
    void shouldCreateMinioClientWhenStorageTypeIsMinio() {
        minioContextRunner().run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(MinioClient.class));
    }

    @Test
    void shouldNotCreateMinioClientWhenStorageTypeIsLocal() {
        contextRunner
                .withPropertyValues("credential-file.storage.type=LOCAL")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(MinioClient.class));
    }

    @Test
    void shouldReuseSameMinioClientBean() {
        minioContextRunner().run(context -> {
            MinioClient first = context.getBean(MinioClient.class);
            MinioClient second = context.getBean(MinioClient.class);

            assertThat(first).isSameAs(second);
        });
    }

    @Test
    void shouldFailWhenMinioEndpointIsMissing() {
        contextRunner
                .withPropertyValues(
                        "credential-file.storage.type=MINIO",
                        "credential-file.storage.minio.access-key=test-access-key",
                        "credential-file.storage.minio.secret-key=test-secret-key",
                        "credential-file.storage.minio.bucket=credential-files")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    private ApplicationContextRunner minioContextRunner() {
        return contextRunner.withPropertyValues(
                "credential-file.storage.type=MINIO",
                "credential-file.storage.minio.endpoint=http://10.0.0.100:9000",
                "credential-file.storage.minio.access-key=test-access-key",
                "credential-file.storage.minio.secret-key=test-secret-key",
                "credential-file.storage.minio.bucket=credential-files");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CredentialFileStorageProperties.class)
    @Import(MinioConfiguration.class)
    static class MinioTestConfiguration {
    }
}
