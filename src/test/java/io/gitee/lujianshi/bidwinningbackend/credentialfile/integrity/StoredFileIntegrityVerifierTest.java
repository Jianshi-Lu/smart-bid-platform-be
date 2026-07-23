package io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoredFileMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoredFileIntegrityVerifierTest {

    private static final String CONTENT = "credential-content";

    private static final String SHA256 =
            "adb3bfe32204b9642febd9c202f8c006438130e794cc1a749065277cbf853d2a";

    @Test
    void shouldReopenStoredFileAndReturnVerifiedStorageSnapshot() throws Exception {
        RecordingStorage storage = new RecordingStorage(CONTENT);
        StoredFileIntegrityVerifier verifier = createVerifier(storage);
        FileStorage.StoredFile storedFile = storedFile(SHA256);

        VerifiedStoredFile verifiedFile =
                verifier.verify(storedFile, "license.pdf", SHA256);

        assertEquals(SHA256, verifiedFile.sha256());
        assertEquals(storedFile.relativePath(), verifiedFile.relativePath());
        assertEquals(storedFile.fileSize(), verifiedFile.fileSize());
        assertEquals(1001, verifiedFile.bidRecordId());
        assertEquals(
                storedFile.metadata().get(StoredFileMetadata.UPLOAD_BATCH_ID),
                verifiedFile.uploadBatchId());
        assertNotNull(storage.openRequest);
        assertEquals(storedFile.relativePath(), storage.openRequest.relativePath());
        assertEquals("etag-1", storage.openRequest.matchEtag());
    }

    @Test
    void shouldRejectStoredContentWhenActualSha256DoesNotMatchMetadata() {
        RecordingStorage storage = new RecordingStorage("tampered-content");
        StoredFileIntegrityVerifier verifier = createVerifier(storage);

        FileIntegrityException exception = assertThrows(
                FileIntegrityException.class,
                () -> verifier.verify(
                        storedFile(SHA256), "license.pdf", SHA256));

        assertEquals("存储对象内容完整性校验失败: batch/original/license.pdf",
                exception.getMessage());
        assertNotNull(storage.openRequest);
    }

    @Test
    void shouldRejectMetadataBeforeOpeningStoredContent() {
        RecordingStorage storage = new RecordingStorage(CONTENT);
        StoredFileIntegrityVerifier verifier = createVerifier(storage);

        assertThrows(
                FileIntegrityException.class,
                () -> verifier.verify(
                        storedFile("0".repeat(64)), "license.pdf", SHA256));

        assertNull(storage.openRequest);
    }

    @Test
    void shouldRejectMissingBusinessMetadataBeforeOpeningStoredContent() {
        RecordingStorage storage = new RecordingStorage(CONTENT);
        StoredFileIntegrityVerifier verifier = createVerifier(storage);
        FileStorage.StoredFile storedFile = new FileStorage.StoredFile(
                "batch/original/license.pdf",
                CONTENT.getBytes(StandardCharsets.UTF_8).length,
                "application/pdf",
                Map.of(StoredFileMetadata.SHA256, SHA256),
                "etag-1");

        assertThrows(
                FileIntegrityException.class,
                () -> verifier.verify(storedFile, "license.pdf", SHA256));

        assertNull(storage.openRequest);
    }

    private StoredFileIntegrityVerifier createVerifier(FileStorage storage) {
        StoredFileIntegrityVerifier verifier = new StoredFileIntegrityVerifier();
        ReflectionTestUtils.setField(verifier, "fileStorage", storage);
        ReflectionTestUtils.setField(
                verifier, "fileChecksumCalculator", new Sha256FileChecksumCalculator());
        return verifier;
    }

    private FileStorage.StoredFile storedFile(String sha256) {
        return new FileStorage.StoredFile(
                "batch/original/license.pdf",
                CONTENT.getBytes(StandardCharsets.UTF_8).length,
                "application/pdf",
                Map.of(
                        StoredFileMetadata.BID_RECORD_ID, "1001",
                        StoredFileMetadata.UPLOAD_BATCH_ID,
                        UUID.randomUUID().toString(),
                        StoredFileMetadata.SHA256, sha256),
                "etag-1");
    }

    private static final class RecordingStorage implements FileStorage {

        private final byte[] content;
        private OpenRequest openRequest;

        private RecordingStorage(String content) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public StoredFile store(StoreRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream open(String relativePath) {
            return new ByteArrayInputStream(content);
        }

        @Override
        public InputStream open(OpenRequest request) {
            this.openRequest = request;
            return new ByteArrayInputStream(content);
        }

        @Override
        public void delete(String relativePath) throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}
