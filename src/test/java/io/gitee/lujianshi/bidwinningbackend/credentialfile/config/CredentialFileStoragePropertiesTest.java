package io.gitee.lujianshi.bidwinningbackend.credentialfile.config;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.FileStorageType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStoragePropertiesTest {

    @Test
    void shouldBindLocalStoragePropertiesAndDefaultToLocalType() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("credential-file.storage.local.root", "/tmp/credential-files");

        FileStorageProperties properties = bind(environment);

        assertEquals(FileStorageType.LOCAL, properties.getType());
        assertEquals("/tmp/credential-files", properties.getLocal().getRoot());
    }

    @Test
    void shouldBindMinioStorageProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("credential-file.storage.type", "minio")
                .withProperty("credential-file.storage.minio.endpoint", "http://10.0.0.100:9000")
                .withProperty("credential-file.storage.minio.access-key", "test-access-key")
                .withProperty("credential-file.storage.minio.secret-key", "test-secret-key")
                .withProperty("credential-file.storage.minio.bucket", "credential-files")
                .withProperty("credential-file.storage.minio.create-bucket-if-absent", "true");

        FileStorageProperties properties = bind(environment);

        assertEquals(FileStorageType.MINIO, properties.getType());
        assertEquals("http://10.0.0.100:9000", properties.getMinio().getEndpoint());
        assertEquals("test-access-key", properties.getMinio().getAccessKey());
        assertEquals("test-secret-key", properties.getMinio().getSecretKey());
        assertEquals("credential-files", properties.getMinio().getBucket());
        assertTrue(properties.getMinio().isCreateBucketIfAbsent());
    }

    private FileStorageProperties bind(MockEnvironment environment) {
        return Binder.get(environment)
                .bind(
                        "credential-file.storage",
                        Bindable.of(FileStorageProperties.class))
                .orElseThrow(() -> new IllegalStateException("资信文件存储配置绑定失败"));
    }
}
