package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoragePathTest {

    @Test
    void shouldCreateUniqueOriginalPathsWithSafeFilename() {
        String batchId = UUID.randomUUID().toString();

        StoragePath.OriginalFilePath first =
                StoragePath.createOriginalFilePath(batchId, "../营业执照.pdf");
        StoragePath.OriginalFilePath second =
                StoragePath.createOriginalFilePath(batchId, "../营业执照.pdf");

        assertEquals("营业执照.pdf", first.safeOriginalFilename());
        assertTrue(first.relativePath().startsWith(batchId + "/original/"));
        assertTrue(first.relativePath().endsWith("_营业执照.pdf"));
        assertNotEquals(first.relativePath(), second.relativePath());
    }
}
