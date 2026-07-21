package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.config.storage.CredentialFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.config.storage.MinioConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialFileStorageConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StorageTestConfiguration.class)
            .withPropertyValues(
                    "credential-file.storage.local.root=/tmp/credential-files",
                    "credential-file.storage.minio.endpoint=http://10.0.0.100:9000",
                    "credential-file.storage.minio.access-key=test-access-key",
                    "credential-file.storage.minio.secret-key=test-secret-key",
                    "credential-file.storage.minio.bucket=credential-files");

    @Test
    void shouldActivateLocalStorageWhenTypeIsMissing() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(CredentialFileStorage.class)
                .hasSingleBean(LocalCredentialFileStorage.class)
                .doesNotHaveBean(MinioCredentialFileStorage.class));
    }

    @Test
    void shouldActivateOnlyLocalStorageWhenTypeIsLocal() {
        contextRunner
                .withPropertyValues("credential-file.storage.type=LOCAL")
                .run(context -> assertThat(context)
                        .hasSingleBean(CredentialFileStorage.class)
                        .hasSingleBean(LocalCredentialFileStorage.class)
                        .doesNotHaveBean(MinioCredentialFileStorage.class));
    }

    @Test
    void shouldActivateOnlyMinioStorageWhenTypeIsMinio() {
        contextRunner
                .withPropertyValues("credential-file.storage.type=MINIO")
                .run(context -> assertThat(context)
                        .hasSingleBean(CredentialFileStorage.class)
                        .hasSingleBean(MinioCredentialFileStorage.class)
                        .doesNotHaveBean(LocalCredentialFileStorage.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CredentialFileStorageProperties.class)
    @Import({
            LocalCredentialFileStorage.class,
            MinioCredentialFileStorage.class,
            MinioConfiguration.class
    })
    static class StorageTestConfiguration {
    }
}
