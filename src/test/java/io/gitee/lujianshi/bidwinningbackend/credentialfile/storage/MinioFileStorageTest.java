package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

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

    private static final int BID_RECORD_ID = 1001;
    private static final String USERNAME = "bidder01";
    private static final String BID_RECORD_ID_METADATA = "bid-record-id";
    private static final String FILE_UUID_METADATA = "file-uuid";
    private static final String SHA256_METADATA = "sha256";
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
                StoragePath.createOriginalFilePath(USERNAME, batchId, "../营业执照.pdf");
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
        assertEquals(batchId, args.userMetadata().getFirst("x-amz-meta-file-uuid"));
        assertEquals(SHA256, args.userMetadata().getFirst("x-amz-meta-sha256"));
        assertSame(input, args.stream());
        assertEquals("credential-files", minioClient.statObjectArgs.bucket());
        assertEquals(args.object(), minioClient.statObjectArgs.object());
        assertEquals(content.length, storedFile.fileSize());
        assertEquals("application/pdf", storedFile.contentType());
        assertEquals(metadata(batchId), storedFile.metadata());
        assertEquals("etag-1", storedFile.etag());
        assertTrue(storedFile.relativePath().startsWith(
                USERNAME + "/营业执照.pdf/original/"));
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
        return new MinioFileStorage(minioClient, "credential-files");
    }

    private FileStorage.StoreRequest request(
            String batchId,
            String filename,
            String contentType,
            byte[] content) {
        return new FileStorage.StoreRequest(
                StoragePath.createOriginalFilePath(USERNAME, batchId, filename).relativePath(),
                contentType,
                content.length,
                metadata(batchId),
                new ByteArrayInputStream(content));
    }

    private Map<String, String> metadata(String batchId) {
        return Map.of(
                BID_RECORD_ID_METADATA, "1001",
                FILE_UUID_METADATA, batchId,
                SHA256_METADATA, SHA256);
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
        private String statFileUuid;
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
                String fileUuid,
                long fileSize,
                String contentType) {
            this.statBidRecordId = "1001";
            this.statFileUuid = fileUuid;
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
            if (statFileUuid != null) {
                headers.add("x-amz-meta-file-uuid", statFileUuid);
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
