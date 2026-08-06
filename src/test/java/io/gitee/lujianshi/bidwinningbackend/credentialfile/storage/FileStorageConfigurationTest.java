package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageConfiguration;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.KnowledgeFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.MinioConfiguration;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StorageTestConfiguration.class)
            .withPropertyValues(
                    "credential-file.storage.local.root=/tmp/credential-files",
                    "knowledge-file.storage.local.root=/tmp/knowledge-files",
                    "credential-file.storage.minio.endpoint=http://10.0.0.100:9000",
                    "credential-file.storage.minio.access-key=test-access-key",
                    "credential-file.storage.minio.secret-key=test-secret-key",
                    "credential-file.storage.minio.bucket=credential-files",
                    "knowledge-file.storage.minio.endpoint=http://10.0.0.100:9000",
                    "knowledge-file.storage.minio.access-key=test-access-key",
                    "knowledge-file.storage.minio.secret-key=test-secret-key",
                    "knowledge-file.storage.minio.bucket=knowledge-file");

    @Test
    void shouldCreateLocalStorageForBothChainsWhenTypeIsLocal() {
        contextRunner
                .withPropertyValues(
                        "credential-file.storage.type=LOCAL",
                        "knowledge-file.storage.type=LOCAL")
                .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(FileStorage.class)).hasSize(2);
            assertThat(context.getBean("credentialFileStorage", FileStorage.class))
                    .isInstanceOf(LocalFileStorage.class);
            assertThat(context.getBean("knowledgeFileStorage", FileStorage.class))
                    .isInstanceOf(LocalFileStorage.class);
            assertThat(context).doesNotHaveBean(MinioClient.class);
        });
    }

    @Test
    void shouldCreateMinioStorageForBothChainsWhenBothAreMinio() {
        contextRunner
                .withPropertyValues(
                        "credential-file.storage.type=MINIO",
                        "knowledge-file.storage.type=MINIO")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("credentialFileStorage", FileStorage.class))
                            .isInstanceOf(MinioFileStorage.class);
                    assertThat(context.getBean("knowledgeFileStorage", FileStorage.class))
                            .isInstanceOf(MinioFileStorage.class);
                    assertThat(ReflectionTestUtils.getField(
                            context.getBean("credentialFileStorage", FileStorage.class),
                            "bucket")).isEqualTo("credential-files");
                    assertThat(ReflectionTestUtils.getField(
                            context.getBean("knowledgeFileStorage", FileStorage.class),
                            "bucket")).isEqualTo("knowledge-file");
                    assertThat(context).hasSingleBean(MinioClient.class);
                });
    }

    @Test
    void shouldSupportMixedStorageTypesPerChain() {
        contextRunner
                .withPropertyValues(
                        "credential-file.storage.type=MINIO",
                        "knowledge-file.storage.type=LOCAL")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("credentialFileStorage", FileStorage.class))
                            .isInstanceOf(MinioFileStorage.class);
                    assertThat(context.getBean("knowledgeFileStorage", FileStorage.class))
                            .isInstanceOf(LocalFileStorage.class);
                    assertThat(context).hasSingleBean(MinioClient.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            FileStorageProperties.class,
            KnowledgeFileStorageProperties.class
    })
    @Import({
            FileStorageConfiguration.class,
            MinioConfiguration.class
    })
    static class StorageTestConfiguration {
    }
}
