package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.config.storage.CredentialFileStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioCredentialFileStorageTest {

    private static final String SHA256 =
            "9cd4aefcb376e737ae07795b61419408249765494085c144e2d83561de56d145";

    @Test
    void shouldUploadOriginalFileWithExpectedObjectMetadata() throws Exception {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioCredentialFileStorage storage = createStorage(minioClient);
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream input = new ByteArrayInputStream(content);

        CredentialFileStorage.StoredFile storedFile = storage.storeOriginal(
                new CredentialFileStorage.StoreOriginalRequest(
                        batchId,
                        "../营业执照.pdf",
                        "application/pdf",
                        content.length,
                        SHA256,
                        input));

        PutObjectArgs args = minioClient.putObjectArgs;
        assertEquals("credential-files", args.bucket());
        assertEquals(storedFile.relativePath(), args.object());
        assertEquals(content.length, args.objectSize());
        assertEquals("application/pdf", args.contentType().toString());
        assertEquals(SHA256, args.userMetadata().getFirst("x-amz-meta-sha256"));
        assertSame(input, args.stream());
        assertEquals("营业执照.pdf", storedFile.safeOriginalFilename());
        assertEquals(content.length, storedFile.fileSize());
        assertTrue(storedFile.relativePath().startsWith(batchId + "/original/"));
    }

    @Test
    void shouldOpenAndDeleteObjectUsingConfiguredBucket() throws Exception {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioCredentialFileStorage storage = createStorage(minioClient);
        String objectKey = UUID.randomUUID() + "/original/file.pdf";

        assertSame(minioClient.getObjectResponse, storage.open(objectKey));
        storage.delete(objectKey);

        assertEquals("credential-files", minioClient.getObjectArgs.bucket());
        assertEquals(objectKey, minioClient.getObjectArgs.object());
        assertEquals("credential-files", minioClient.removeObjectArgs.bucket());
        assertEquals(objectKey, minioClient.removeObjectArgs.object());
    }

    @Test
    void shouldWrapMinioSdkExceptionAsIOException() {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        minioClient.putFailure = new MinioException("upload failed");
        MinioCredentialFileStorage storage = createStorage(minioClient);
        byte[] content = new byte[]{1};

        IOException exception = assertThrows(
                IOException.class,
                () -> storage.storeOriginal(
                        new CredentialFileStorage.StoreOriginalRequest(
                                UUID.randomUUID().toString(),
                                "license.pdf",
                                "application/pdf",
                                content.length,
                                SHA256,
                                new ByteArrayInputStream(content))));

        assertInstanceOf(MinioException.class, exception.getCause());
        assertTrue(exception.getMessage().startsWith("MinIO 原始文件上传失败:"));
    }

    @Test
    void shouldRejectUnsafeObjectKeyBeforeCallingMinio() {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioCredentialFileStorage storage = createStorage(minioClient);

        assertThrows(IllegalArgumentException.class, () -> storage.open("../outside.pdf"));
        assertThrows(IllegalArgumentException.class, () -> storage.delete("/outside.pdf"));
        assertNull(minioClient.getObjectArgs);
        assertNull(minioClient.removeObjectArgs);
    }

    private MinioCredentialFileStorage createStorage(MinioClient minioClient) {
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getMinio().setBucket("credential-files");
        MinioCredentialFileStorage storage = new MinioCredentialFileStorage();
        ReflectionTestUtils.setField(storage, "minioClient", minioClient);
        ReflectionTestUtils.setField(storage, "properties", properties);
        return storage;
    }

    private static final class RecordingMinioClient extends MinioClient {

        private PutObjectArgs putObjectArgs;
        private GetObjectArgs getObjectArgs;
        private RemoveObjectArgs removeObjectArgs;
        private MinioException putFailure;
        private final GetObjectResponse getObjectResponse;

        private RecordingMinioClient() {
            super(MinioClient.builder()
                    .endpoint("http://127.0.0.1:9000")
                    .credentials("test-access-key", "test-secret-key")
                    .build());
            this.getObjectResponse = new GetObjectResponse(
                    new Headers.Builder().build(),
                    "credential-files",
                    null,
                    "object",
                    new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public ObjectWriteResponse putObject(PutObjectArgs args) throws MinioException {
            this.putObjectArgs = args;
            if (putFailure != null) {
                throw putFailure;
            }
            return null;
        }

        @Override
        public GetObjectResponse getObject(GetObjectArgs args) {
            this.getObjectArgs = args;
            return getObjectResponse;
        }

        @Override
        public void removeObject(RemoveObjectArgs args) {
            this.removeObjectArgs = args;
        }
    }
}
