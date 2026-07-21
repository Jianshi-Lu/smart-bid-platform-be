package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.config.storage.CredentialFileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalCredentialFileStorageTest {

    private static final String VALID_SHA256 = "0".repeat(64);

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAndReopenFileUsingRelativePath() throws Exception {
        LocalCredentialFileStorage storage = createStorage();
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);

        CredentialFileStorage.StoredFile storedFile = storage.storeOriginal(
                request(batchId, "../营业执照.pdf", "application/pdf", content));

        assertEquals("营业执照.pdf", storedFile.safeOriginalFilename());
        assertEquals(content.length, storedFile.fileSize());
        assertTrue(storedFile.relativePath().startsWith(batchId + "/original/"));
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
        LocalCredentialFileStorage storage = createStorage();
        String batchId = UUID.randomUUID().toString();

        CredentialFileStorage.StoredFile first = storage.storeOriginal(
                request(batchId, "license.pdf", "application/pdf", new byte[]{1}));
        CredentialFileStorage.StoredFile second = storage.storeOriginal(
                request(batchId, "license.pdf", "application/pdf", new byte[]{2}));

        assertNotEquals(first.relativePath(), second.relativePath());
        assertTrue(Files.exists(tempDir.resolve(first.relativePath())));
        assertTrue(Files.exists(tempDir.resolve(second.relativePath())));
    }

    @Test
    void shouldRejectPathOutsideStorageRoot() {
        LocalCredentialFileStorage storage = createStorage();

        assertThrows(IllegalArgumentException.class, () -> storage.open("../../outside.txt"));
    }

    @Test
    void shouldDeleteFileWhenDeclaredLengthDoesNotMatchContent() throws Exception {
        LocalCredentialFileStorage storage = createStorage();
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        CredentialFileStorage.StoreOriginalRequest request =
                new CredentialFileStorage.StoreOriginalRequest(
                        1001,
                        UUID.randomUUID().toString(),
                        "license.pdf",
                        "application/pdf",
                        content.length + 1L,
                        VALID_SHA256,
                        new ByteArrayInputStream(content));

        assertThrows(IOException.class, () -> storage.storeOriginal(request));
        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    void shouldRejectInvalidSha256BeforeStorage() {
        String batchId = UUID.randomUUID().toString();
        byte[] content = new byte[]{1};

        assertThrows(IllegalArgumentException.class, () -> new CredentialFileStorage.StoreOriginalRequest(
                1001, batchId, "license.pdf", "application/pdf", content.length, null,
                new ByteArrayInputStream(content)));
        assertThrows(IllegalArgumentException.class, () -> new CredentialFileStorage.StoreOriginalRequest(
                1001, batchId, "license.pdf", "application/pdf", content.length, "",
                new ByteArrayInputStream(content)));
        assertThrows(IllegalArgumentException.class, () -> new CredentialFileStorage.StoreOriginalRequest(
                1001, batchId, "license.pdf", "application/pdf", content.length, "abc",
                new ByteArrayInputStream(content)));
        assertThrows(IllegalArgumentException.class, () -> new CredentialFileStorage.StoreOriginalRequest(
                1001, batchId, "license.pdf", "application/pdf", content.length, "A".repeat(64),
                new ByteArrayInputStream(content)));
    }

    private LocalCredentialFileStorage createStorage() {
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new LocalCredentialFileStorage(properties);
    }

    private CredentialFileStorage.StoreOriginalRequest request(
            String batchId,
            String filename,
            String contentType,
            byte[] content) {
        return new CredentialFileStorage.StoreOriginalRequest(
                1001,
                batchId,
                filename,
                contentType,
                content.length,
                VALID_SHA256,
                new ByteArrayInputStream(content));
    }
}
