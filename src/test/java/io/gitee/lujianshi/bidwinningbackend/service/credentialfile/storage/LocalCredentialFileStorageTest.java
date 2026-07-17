package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
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

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAndReopenFileUsingRelativePath() throws Exception {
        LocalCredentialFileStorage storage = new LocalCredentialFileStorage(tempDir.toString());
        String batchId = UUID.randomUUID().toString();
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);

        CredentialFileStorage.StoredFile storedFile = storage.storeOriginal(
                batchId,
                "../营业执照.pdf",
                new ByteArrayInputStream(content));

        assertEquals("营业执照.pdf", storedFile.originalFilename());
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
        LocalCredentialFileStorage storage = new LocalCredentialFileStorage(tempDir.toString());
        String batchId = UUID.randomUUID().toString();

        CredentialFileStorage.StoredFile first = storage.storeOriginal(
                batchId, "license.pdf", new ByteArrayInputStream(new byte[]{1}));
        CredentialFileStorage.StoredFile second = storage.storeOriginal(
                batchId, "license.pdf", new ByteArrayInputStream(new byte[]{2}));

        assertNotEquals(first.relativePath(), second.relativePath());
        assertTrue(Files.exists(tempDir.resolve(first.relativePath())));
        assertTrue(Files.exists(tempDir.resolve(second.relativePath())));
    }

    @Test
    void shouldRejectPathOutsideStorageRoot() {
        LocalCredentialFileStorage storage = new LocalCredentialFileStorage(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> storage.open("../../outside.txt"));
    }
}
