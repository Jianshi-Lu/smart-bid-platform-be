package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.CredentialFileStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.HeadObjectResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.MinioException;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioFileStorageTest {

    private static final String SHA256 =
            "9cd4aefcb376e737ae07795b61419408249765494085c144e2d83561de56d145";

    @Test
    void shouldUploadOriginalFileWithExpectedObjectMetadata() throws Exception {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioFileStorage storage = createStorage(minioClient);
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream input = new ByteArrayInputStream(content);
        minioClient.prepareStoredObject(batchId, content.length, "application/pdf");

        StoragePath.OriginalFilePath storagePath =
                StoragePath.createOriginalFilePath(batchId, "../营业执照.pdf");
        FileStorage.StoredFile storedFile = storage.store(new FileStorage.StoreRequest(
                storagePath.relativePath(),
                "application/pdf",
                content.length,
                metadata(batchId),
                input));

        PutObjectArgs args = minioClient.putObjectArgs;
        assertEquals("credential-files", args.bucket());
        assertEquals(storedFile.relativePath(), args.object());
        assertEquals(content.length, args.objectSize());
        assertEquals("application/pdf", args.contentType().toString());
        assertEquals("1001", args.userMetadata().getFirst("x-amz-meta-bid-record-id"));
        assertEquals(batchId, args.userMetadata().getFirst("x-amz-meta-upload-batch-id"));
        assertEquals(SHA256, args.userMetadata().getFirst("x-amz-meta-sha256"));
        assertSame(input, args.stream());
        assertEquals("credential-files", minioClient.statObjectArgs.bucket());
        assertEquals(args.object(), minioClient.statObjectArgs.object());
        assertEquals(content.length, storedFile.fileSize());
        assertEquals("application/pdf", storedFile.contentType());
        assertEquals(metadata(batchId), storedFile.metadata());
        assertEquals("etag-1", storedFile.etag());
        assertTrue(storedFile.relativePath().startsWith(batchId + "/original/"));
    }

    @Test
    void shouldDeleteUploadedObjectWhenMetadataDoesNotMatch() {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioFileStorage storage = createStorage(minioClient);
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);
        minioClient.prepareStoredObject(batchId, content.length, "application/pdf");
        minioClient.statSha256 = "0".repeat(64);

        IOException exception = assertThrows(
                IOException.class,
                () -> storage.store(request(
                        batchId, "license.pdf", "application/pdf", content)));

        assertTrue(exception.getMessage().contains("metadata 不一致"));
        assertNotNull(minioClient.removeObjectArgs);
        assertEquals(minioClient.putObjectArgs.object(), minioClient.removeObjectArgs.object());
    }

    @Test
    void shouldDeleteUploadedObjectWhenRequiredMetadataIsMissing() {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioFileStorage storage = createStorage(minioClient);
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);
        minioClient.prepareStoredObject(batchId, content.length, "application/pdf");
        minioClient.statBidRecordId = null;

        IOException exception = assertThrows(
                IOException.class,
                () -> storage.store(request(
                        batchId, "license.pdf", "application/pdf", content)));

        assertTrue(exception.getMessage().contains("bid-record-id"));
        assertNotNull(minioClient.removeObjectArgs);
    }

    @Test
    void shouldDeleteUploadedObjectWhenEtagDoesNotMatch() {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioFileStorage storage = createStorage(minioClient);
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);
        minioClient.prepareStoredObject(batchId, content.length, "application/pdf");
        minioClient.statEtag = "other-etag";

        IOException etagException = assertThrows(
                IOException.class,
                () -> storage.store(request(
                        batchId, "license.pdf", "application/pdf", content)));

        assertTrue(etagException.getMessage().contains("ETag"));
        assertNotNull(minioClient.removeObjectArgs);
    }

    @Test
    void shouldDeleteUploadedObjectWhenStatFails() {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioFileStorage storage = createStorage(minioClient);
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);
        minioClient.prepareStoredObject(batchId, content.length, "application/pdf");
        minioClient.statFailure = new MinioException("stat failed");

        IOException exception = assertThrows(
                IOException.class,
                () -> storage.store(request(
                        batchId, "license.pdf", "application/pdf", content)));

        assertInstanceOf(MinioException.class, exception.getCause());
        assertNotNull(minioClient.removeObjectArgs);
        assertEquals(minioClient.putObjectArgs.object(), minioClient.removeObjectArgs.object());
    }

    @Test
    void shouldPreserveOriginalFailureWhenCleanupFails() {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioFileStorage storage = createStorage(minioClient);
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);
        minioClient.prepareStoredObject(batchId, content.length + 1L, "application/pdf");
        minioClient.removeFailure = new MinioException("cleanup failed");

        IOException exception = assertThrows(
                IOException.class,
                () -> storage.store(request(
                        batchId, "license.pdf", "application/pdf", content)));

        assertTrue(exception.getMessage().contains("对象大小"));
        assertEquals(1, exception.getSuppressed().length);
        assertSame(minioClient.removeFailure, exception.getSuppressed()[0]);
    }

    @Test
    void shouldOpenAndDeleteObjectUsingConfiguredBucket() throws Exception {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioFileStorage storage = createStorage(minioClient);
        String objectKey = UUID.randomUUID() + "/original/file.pdf";

        assertSame(
                minioClient.getObjectResponse,
                storage.open(new FileStorage.OpenRequest(objectKey, "etag-1")));
        storage.delete(objectKey);

        assertEquals("credential-files", minioClient.getObjectArgs.bucket());
        assertEquals(objectKey, minioClient.getObjectArgs.object());
        assertEquals("etag-1", minioClient.getObjectArgs.matchETag());
        assertEquals("credential-files", minioClient.removeObjectArgs.bucket());
        assertEquals(objectKey, minioClient.removeObjectArgs.object());
    }

    @Test
    void shouldWrapMinioSdkExceptionAsIOException() {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        minioClient.putFailure = new MinioException("upload failed");
        MinioFileStorage storage = createStorage(minioClient);
        byte[] content = new byte[]{1};

        IOException exception = assertThrows(
                IOException.class,
                () -> storage.store(
                        request(
                                UUID.randomUUID().toString(),
                                "license.pdf",
                                "application/pdf",
                                content)));

        assertInstanceOf(MinioException.class, exception.getCause());
        assertTrue(exception.getMessage().startsWith("MinIO 文件上传失败:"));
    }

    @Test
    void shouldRejectUnsafeObjectKeyBeforeCallingMinio() {
        RecordingMinioClient minioClient = new RecordingMinioClient();
        MinioFileStorage storage = createStorage(minioClient);

        assertThrows(IllegalArgumentException.class, () -> storage.open("../outside.pdf"));
        assertThrows(IllegalArgumentException.class, () -> storage.delete("/outside.pdf"));
        assertNull(minioClient.getObjectArgs);
        assertNull(minioClient.removeObjectArgs);
    }

    private MinioFileStorage createStorage(MinioClient minioClient) {
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getMinio().setBucket("credential-files");
        MinioFileStorage storage = new MinioFileStorage();
        ReflectionTestUtils.setField(storage, "minioClient", minioClient);
        ReflectionTestUtils.setField(storage, "properties", properties);
        return storage;
    }

    private FileStorage.StoreRequest request(
            String batchId,
            String filename,
            String contentType,
            byte[] content) {
        return new FileStorage.StoreRequest(
                StoragePath.createOriginalFilePath(batchId, filename).relativePath(),
                contentType,
                content.length,
                metadata(batchId),
                new ByteArrayInputStream(content));
    }

    private Map<String, String> metadata(String batchId) {
        return Map.of(
                StoredFileMetadata.BID_RECORD_ID, "1001",
                StoredFileMetadata.UPLOAD_BATCH_ID, batchId,
                StoredFileMetadata.SHA256, SHA256);
    }

    private static final class RecordingMinioClient extends MinioClient {

        private PutObjectArgs putObjectArgs;
        private StatObjectArgs statObjectArgs;
        private GetObjectArgs getObjectArgs;
        private RemoveObjectArgs removeObjectArgs;
        private MinioException putFailure;
        private MinioException statFailure;
        private MinioException removeFailure;
        private String statBidRecordId;
        private String statUploadBatchId;
        private String statSha256;
        private long statFileSize;
        private String statContentType;
        private String writeEtag = "etag-1";
        private String statEtag = "etag-1";
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

        private void prepareStoredObject(
                String uploadBatchId,
                long fileSize,
                String contentType) {
            this.statBidRecordId = "1001";
            this.statUploadBatchId = uploadBatchId;
            this.statSha256 = SHA256;
            this.statFileSize = fileSize;
            this.statContentType = contentType;
        }

        @Override
        public ObjectWriteResponse putObject(PutObjectArgs args) throws MinioException {
            this.putObjectArgs = args;
            if (putFailure != null) {
                throw putFailure;
            }
            return new ObjectWriteResponse(
                    new Headers.Builder().build(),
                    args.bucket(),
                    args.region(),
                    args.object(),
                    writeEtag,
                    null);
        }

        @Override
        public StatObjectResponse statObject(StatObjectArgs args) throws MinioException {
            this.statObjectArgs = args;
            if (statFailure != null) {
                throw statFailure;
            }
            Headers.Builder headers = new Headers.Builder()
                    .add("ETag", "\"" + statEtag + "\"")
                    .add("Content-Length", Long.toString(statFileSize))
                    .add("Content-Type", statContentType)
                    .add("Last-Modified", "Tue, 15 Nov 1994 12:45:26 GMT");
            if (statBidRecordId != null) {
                headers.add("x-amz-meta-bid-record-id", statBidRecordId);
            }
            if (statUploadBatchId != null) {
                headers.add("x-amz-meta-upload-batch-id", statUploadBatchId);
            }
            if (statSha256 != null) {
                headers.add("x-amz-meta-sha256", statSha256);
            }
            return new StatObjectResponse(new HeadObjectResponse(
                    headers.build(), args.bucket(), args.region(), args.object()));
        }

        @Override
        public GetObjectResponse getObject(GetObjectArgs args) {
            this.getObjectArgs = args;
            return getObjectResponse;
        }

        @Override
        public void removeObject(RemoveObjectArgs args) throws MinioException {
            this.removeObjectArgs = args;
            if (removeFailure != null) {
                throw removeFailure;
            }
        }
    }
}
