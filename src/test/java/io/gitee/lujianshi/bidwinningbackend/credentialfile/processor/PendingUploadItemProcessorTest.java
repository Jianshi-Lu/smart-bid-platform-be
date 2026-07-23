package io.gitee.lujianshi.bidwinningbackend.credentialfile.processor;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.CredentialFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.ItemContent;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.PendingUploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatchContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity.FileIntegrityException;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity.StoredFileIntegrityVerifier;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity.VerifiedStoredFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoredFileMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PendingUploadItemProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCalculateChecksumBeforeStorageAndOpenItemContentTwice() throws Exception {
        RecordingLocalFileStorage storage = createRecordingStorage();
        Sha256FileChecksumCalculator calculator = new Sha256FileChecksumCalculator();
        PendingUploadItemProcessor processor = createProcessor(calculator, storage);
        byte[] content = "first-content".getBytes(StandardCharsets.UTF_8);
        AtomicInteger openCount = new AtomicInteger();
        ItemContent itemContent = () -> {
            openCount.incrementAndGet();
            return new ByteArrayInputStream(content);
        };

        VerifiedStoredFile verifiedFile = processor.process(
                new UploadBatchContext(1001, UUID.randomUUID().toString()),
                new PendingUploadItem(
                        "license.pdf",
                        "application/pdf",
                        content.length,
                        itemContent));

        String expectedSha256 =
                "9cd4aefcb376e737ae07795b61419408249765494085c144e2d83561de56d145";
        assertEquals(2, openCount.get());
        assertNotNull(storage.storeRequest);
        assertEquals(
                expectedSha256,
                storage.storeRequest.metadata().get(StoredFileMetadata.SHA256));
        assertEquals(expectedSha256, verifiedFile.sha256());
        assertEquals(1, storage.openCalls);
    }

    @Test
    void shouldNotStoreCurrentFileWhenPreUploadChecksumFails() {
        RecordingLocalFileStorage storage = createRecordingStorage();
        FileChecksumCalculator failingCalculator = new FileChecksumCalculator() {
            @Override
            public String algorithm() {
                return "SHA-256";
            }

            @Override
            public String calculate(InputStream input) throws IOException {
                throw new IOException("checksum failed");
            }
        };
        PendingUploadItemProcessor processor =
                createProcessor(failingCalculator, storage);

        assertThrows(
                IOException.class,
                () -> processor.process(
                        new UploadBatchContext(1001, UUID.randomUUID().toString()),
                        item("license.pdf", "content")));

        assertNull(storage.storeRequest);
    }

    @Test
    void shouldDeleteCurrentStoredFileWhenIntegrityVerificationFails() throws Exception {
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        FileStorage storage = new TamperingLocalFileStorage(properties);
        PendingUploadItemProcessor processor =
                createProcessor(new Sha256FileChecksumCalculator(), storage);

        assertThrows(
                FileIntegrityException.class,
                () -> processor.process(
                        new UploadBatchContext(1001, UUID.randomUUID().toString()),
                        item("license.pdf", "original-content")));

        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    void shouldDeleteStoredFileWhenUploadInputCloseFails() throws Exception {
        RecordingLocalFileStorage storage = createRecordingStorage();
        PendingUploadItemProcessor processor =
                createProcessor(new Sha256FileChecksumCalculator(), storage);
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);
        AtomicInteger openCount = new AtomicInteger();
        ItemContent itemContent = () -> {
            InputStream input = new ByteArrayInputStream(content);
            if (openCount.incrementAndGet() == 2) {
                return new FilterInputStream(input) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        throw new IOException("close failed");
                    }
                };
            }
            return input;
        };

        assertThrows(
                IOException.class,
                () -> processor.process(
                        new UploadBatchContext(1001, UUID.randomUUID().toString()),
                        new PendingUploadItem(
                                "license.pdf",
                                "application/pdf",
                                content.length,
                                itemContent)));

        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    private PendingUploadItemProcessor createProcessor(
            FileChecksumCalculator calculator,
            FileStorage storage) {
        StoredFileIntegrityVerifier verifier = new StoredFileIntegrityVerifier();
        ReflectionTestUtils.setField(verifier, "fileStorage", storage);
        ReflectionTestUtils.setField(verifier, "fileChecksumCalculator", calculator);

        PendingUploadItemProcessor processor = new PendingUploadItemProcessor();
        ReflectionTestUtils.setField(processor, "fileStorage", storage);
        ReflectionTestUtils.setField(processor, "fileChecksumCalculator", calculator);
        ReflectionTestUtils.setField(processor, "storedFileIntegrityVerifier", verifier);
        return processor;
    }

    private RecordingLocalFileStorage createRecordingStorage() {
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new RecordingLocalFileStorage(properties);
    }

    private PendingUploadItem item(String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new PendingUploadItem(
                filename,
                "application/pdf",
                bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    private static final class RecordingLocalFileStorage extends LocalFileStorage {

        private FileStorage.StoreRequest storeRequest;
        private int openCalls;

        private RecordingLocalFileStorage(
                CredentialFileStorageProperties properties) {
            super(properties);
        }

        @Override
        public StoredFile store(StoreRequest request) throws IOException {
            this.storeRequest = request;
            return super.store(request);
        }

        @Override
        public InputStream open(String relativePath) throws IOException {
            openCalls++;
            return super.open(relativePath);
        }
    }

    private static final class TamperingLocalFileStorage extends LocalFileStorage {

        private TamperingLocalFileStorage(
                CredentialFileStorageProperties properties) {
            super(properties);
        }

        @Override
        public InputStream open(String relativePath) {
            return new ByteArrayInputStream(
                    "tampered-content".getBytes(StandardCharsets.UTF_8));
        }
    }
}
