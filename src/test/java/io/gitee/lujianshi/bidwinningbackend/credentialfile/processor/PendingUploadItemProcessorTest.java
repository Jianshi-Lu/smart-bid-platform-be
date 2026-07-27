package io.gitee.lujianshi.bidwinningbackend.credentialfile.processor;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatchContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
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

class UploadItemProcessorTest {

    private static final String SHA256_METADATA = "sha256";

    @TempDir
    Path tempDir;

    @Test
    void shouldCalculateChecksumBeforeStorageAndOpenItemContentTwice() throws Exception {
        RecordingLocalFileStorage storage = createRecordingStorage();
        Sha256FileChecksumCalculator calculator = new Sha256FileChecksumCalculator();
        UploadItemProcessor processor = createProcessor(calculator, storage);
        byte[] content = "first-content".getBytes(StandardCharsets.UTF_8);
        AtomicInteger openCount = new AtomicInteger();
        UploadItem.Content itemContent = () -> {
            openCount.incrementAndGet();
            return new ByteArrayInputStream(content);
        };

        VerifiedStoredFileMetadata verifiedMetadata = processor.process(
                new UploadBatchContext(1001, UUID.randomUUID().toString()),
                new UploadItem(
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
                storage.storeRequest.metadata().get(SHA256_METADATA));
        assertEquals(expectedSha256, verifiedMetadata.sha256());
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
        UploadItemProcessor processor =
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
        FileStorageProperties properties = new FileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        FileStorage storage = new TamperingLocalFileStorage(properties);
        UploadItemProcessor processor =
                createProcessor(new Sha256FileChecksumCalculator(), storage);

        assertThrows(
                AppException.class,
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
        UploadItemProcessor processor =
                createProcessor(new Sha256FileChecksumCalculator(), storage);
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);
        AtomicInteger openCount = new AtomicInteger();
        UploadItem.Content itemContent = () -> {
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
                        new UploadItem(
                                "license.pdf",
                                "application/pdf",
                                content.length,
                                itemContent)));

        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    private UploadItemProcessor createProcessor(
            FileChecksumCalculator calculator,
            FileStorage storage) {
        UploadItemProcessor processor = new UploadItemProcessor();
        ReflectionTestUtils.setField(processor, "fileStorage", storage);
        ReflectionTestUtils.setField(processor, "fileChecksumCalculator", calculator);
        return processor;
    }

    private RecordingLocalFileStorage createRecordingStorage() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new RecordingLocalFileStorage(properties);
    }

    private UploadItem item(String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new UploadItem(
                filename,
                "application/pdf",
                bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    private static final class RecordingLocalFileStorage extends LocalFileStorage {

        private FileStorage.StoreRequest storeRequest;
        private int openCalls;

        private RecordingLocalFileStorage(
            FileStorageProperties properties) {
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
            FileStorageProperties properties) {
            super(properties);
        }

        @Override
        public InputStream open(String relativePath) {
            return new ByteArrayInputStream(
                    "tampered-content".getBytes(StandardCharsets.UTF_8));
        }
    }
}
