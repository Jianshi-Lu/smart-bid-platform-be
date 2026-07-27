package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageTest {

    private static final int BID_RECORD_ID = 1001;
    private static final String BID_RECORD_ID_METADATA = "bid-record-id";
    private static final String UPLOAD_BATCH_ID_METADATA = "upload-batch-id";
    private static final String SHA256_METADATA = "sha256";
    private static final String VALID_SHA256 = "0".repeat(64);

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAndReopenFileUsingRelativePath() throws Exception {
        LocalFileStorage storage = createStorage();
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);

        FileStorage.StoredFile storedFile = storage.store(
                request(batchId, "../营业执照.pdf", "application/pdf", content));

        assertEquals(content.length, storedFile.fileSize());
        assertEquals(metadata(batchId), storedFile.metadata());
        assertTrue(storedFile.relativePath().startsWith(
                "bid-records/" + BID_RECORD_ID + "/batches/" + batchId + "/original/"));
        assertFalse(storedFile.relativePath().startsWith(tempDir.toString()));
        assertTrue(Files.isRegularFile(tempDir.resolve(storedFile.relativePath())));
        try (InputStream input = storage.open(storedFile.relativePath())) {
            assertArrayEquals(content, input.readAllBytes());
        }

        storage.delete(storedFile.relativePath());
        assertFalse(Files.exists(tempDir.resolve(storedFile.relativePath())));
    }

    @Test
    void shouldGenerateDifferentStoredPathsForSameOriginalFilename() throws Exception {
        LocalFileStorage storage = createStorage();
        String batchId = UUID.randomUUID().toString();

        FileStorage.StoredFile first = storage.store(
                request(batchId, "license.pdf", "application/pdf", new byte[]{1}));
        FileStorage.StoredFile second = storage.store(
                request(batchId, "license.pdf", "application/pdf", new byte[]{2}));

        assertNotEquals(first.relativePath(), second.relativePath());
        assertTrue(Files.exists(tempDir.resolve(first.relativePath())));
        assertTrue(Files.exists(tempDir.resolve(second.relativePath())));
    }

    @Test
    void shouldReplaceExistingDerivedFileAtSamePath() throws Exception {
        LocalFileStorage storage = createStorage();
        String path = StoragePath.createMergedMarkdownPath(BID_RECORD_ID);

        storage.store(new FileStorage.StoreRequest(
                path, "text/markdown", 3, Map.of(),
                new ByteArrayInputStream("old".getBytes(StandardCharsets.UTF_8))));
        storage.store(new FileStorage.StoreRequest(
                path, "text/markdown", 3, Map.of(),
                new ByteArrayInputStream("new".getBytes(StandardCharsets.UTF_8))));

        try (InputStream input = storage.open(path)) {
            assertEquals("new", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void shouldRejectPathOutsideStorageRoot() {
        LocalFileStorage storage = createStorage();

        assertThrows(IllegalArgumentException.class, () -> storage.open("../../outside.txt"));
    }

    @Test
    void shouldDeleteFileWhenDeclaredLengthDoesNotMatchContent() throws Exception {
        LocalFileStorage storage = createStorage();
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        String batchId = UUID.randomUUID().toString();
        StoragePath.OriginalFilePath storagePath =
                StoragePath.createOriginalFilePath(BID_RECORD_ID, batchId, "license.pdf");
        FileStorage.StoreRequest request =
                new FileStorage.StoreRequest(
                        storagePath.relativePath(),
                        "application/pdf",
                        content.length + 1L,
                        metadata(batchId),
                        new ByteArrayInputStream(content));

        assertThrows(IOException.class, () -> storage.store(request));
        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    void shouldRejectInvalidGenericStoreRequest() {
        byte[] content = new byte[]{1};

        assertThrows(IllegalArgumentException.class, () -> new FileStorage.StoreRequest(
                null, "application/pdf", content.length, Map.of(),
                new ByteArrayInputStream(content)));
        assertThrows(IllegalArgumentException.class, () -> new FileStorage.StoreRequest(
                "batch/file.pdf", "application/pdf", -1, Map.of(),
                new ByteArrayInputStream(content)));
        assertThrows(IllegalArgumentException.class, () -> new FileStorage.StoreRequest(
                "batch/file.pdf", "application/pdf", content.length, Map.of(), null));
    }

    private LocalFileStorage createStorage() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new LocalFileStorage(properties);
    }

    private FileStorage.StoreRequest request(
            String batchId,
            String filename,
            String contentType,
            byte[] content) {
        return new FileStorage.StoreRequest(
                StoragePath.createOriginalFilePath(BID_RECORD_ID, batchId, filename).relativePath(),
                contentType,
                content.length,
                metadata(batchId),
                new ByteArrayInputStream(content));
    }

    private Map<String, String> metadata(String batchId) {
        return Map.of(
                BID_RECORD_ID_METADATA, "1001",
                UPLOAD_BATCH_ID_METADATA, batchId,
                SHA256_METADATA, VALID_SHA256);
    }
}
