package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.config.properties.CredentialFileStorageProperties;
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
                    "credential-file.storage.local.root=/tmp/credential-files");

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
    @Import({LocalCredentialFileStorage.class, MinioCredentialFileStorage.class})
    static class StorageTestConfiguration {
    }
}
