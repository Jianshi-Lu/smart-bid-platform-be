package io.gitee.lujianshi.bidwinningbackend.storage.object;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
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

class LocalObjectStorageTest {

    private static final int BID_RECORD_ID = 1001;
    private static final String USERNAME = "bidder01";
    private static final String BID_RECORD_ID_METADATA = "bid-record-id";
    private static final String FILE_UUID_METADATA = "file-uuid";
    private static final String SHA256_METADATA = "sha256";
    private static final String VALID_SHA256 = "0".repeat(64);

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAndReopenFileUsingRelativePath() throws Exception {
        LocalObjectStorage storage = createStorage();
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);

        ObjectStorage.StoredFile storedFile = storage.store(
                request(batchId, "../营业执照.pdf", "application/pdf", content));

        assertEquals(content.length, storedFile.fileSize());
        assertEquals(metadata(batchId), storedFile.metadata());
        assertTrue(storedFile.relativePath().startsWith(
                USERNAME + "/营业执照.pdf/original/"));
        assertFalse(storedFile.relativePath().startsWith(tempDir.toString()));
        assertTrue(Files.isRegularFile(tempDir.resolve(storedFile.relativePath())));
        try (InputStream input = storage.open(storedFile.relativePath())) {
            assertArrayEquals(content, input.readAllBytes());
        }

        storage.delete(storedFile.relativePath());
        assertFalse(Files.exists(tempDir.resolve(storedFile.relativePath())));
    }

    @Test
    void shouldOverwriteOriginalPathForSameFileUuid() throws Exception {
        LocalObjectStorage storage = createStorage();
        String fileUuid = UUID.randomUUID().toString();

        ObjectStorage.StoredFile first = storage.store(
                request(fileUuid, "license.pdf", "application/pdf", new byte[]{1}));
        ObjectStorage.StoredFile second = storage.store(
                request(fileUuid, "license.pdf", "application/pdf", new byte[]{2}));

        assertEquals(first.relativePath(), second.relativePath());
        assertTrue(Files.exists(tempDir.resolve(first.relativePath())));
        try (InputStream input = storage.open(first.relativePath())) {
            assertEquals(2, input.readAllBytes()[0]);
        }
    }

    @Test
    void shouldReplaceExistingDerivedFileAtSamePath() throws Exception {
        LocalObjectStorage storage = createStorage();
        String path = StoragePath.createMergedMarkdownPath(BID_RECORD_ID, USERNAME);

        storage.store(new ObjectStorage.StoreRequest(
                path, "text/markdown", 3, Map.of(),
                new ByteArrayInputStream("old".getBytes(StandardCharsets.UTF_8))));
        storage.store(new ObjectStorage.StoreRequest(
                path, "text/markdown", 3, Map.of(),
                new ByteArrayInputStream("new".getBytes(StandardCharsets.UTF_8))));

        try (InputStream input = storage.open(path)) {
            assertEquals("new", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void shouldRejectPathOutsideStorageRoot() {
        LocalObjectStorage storage = createStorage();

        assertThrows(IllegalArgumentException.class, () -> storage.open("../../outside.txt"));
    }

    @Test
    void shouldDeleteFileWhenDeclaredLengthDoesNotMatchContent() throws Exception {
        LocalObjectStorage storage = createStorage();
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        String batchId = UUID.randomUUID().toString();
        StoragePath.OriginalFilePath storagePath =
                StoragePath.createOriginalFilePath(USERNAME, batchId, "license.pdf");
        ObjectStorage.StoreRequest request =
                new ObjectStorage.StoreRequest(
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

        assertThrows(IllegalArgumentException.class, () -> new ObjectStorage.StoreRequest(
                null, "application/pdf", content.length, Map.of(),
                new ByteArrayInputStream(content)));
        assertThrows(IllegalArgumentException.class, () -> new ObjectStorage.StoreRequest(
                "batch/file.pdf", "application/pdf", -1, Map.of(),
                new ByteArrayInputStream(content)));
        assertThrows(IllegalArgumentException.class, () -> new ObjectStorage.StoreRequest(
                "batch/file.pdf", "application/pdf", content.length, Map.of(), null));
    }

    private LocalObjectStorage createStorage() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new LocalObjectStorage(properties.getLocal().getRoot());
    }

    private ObjectStorage.StoreRequest request(
            String batchId,
            String filename,
            String contentType,
            byte[] content) {
        return new ObjectStorage.StoreRequest(
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
                SHA256_METADATA, VALID_SHA256);
    }
}
