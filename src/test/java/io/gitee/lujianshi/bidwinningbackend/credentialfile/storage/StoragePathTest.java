package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoragePathTest {

    private static final int BID_RECORD_ID = 1001;

    @Test
    void shouldCreateUniqueOriginalPathsWithSafeFilename() {
        String batchId = UUID.randomUUID().toString();

        StoragePath.OriginalFilePath first =
                StoragePath.createOriginalFilePath(BID_RECORD_ID, batchId, "../营业执照.pdf");
        StoragePath.OriginalFilePath second =
                StoragePath.createOriginalFilePath(BID_RECORD_ID, batchId, "../营业执照.pdf");

        assertEquals("营业执照.pdf", first.safeOriginalFilename());
        assertTrue(first.relativePath().startsWith(
                "bid-records/" + BID_RECORD_ID + "/batches/" + batchId + "/original/"));
        assertTrue(first.relativePath().endsWith("_营业执照.pdf"));
        assertNotEquals(first.relativePath(), second.relativePath());
    }

    @Test
    void shouldCreateStableMergedMarkdownPathUnderBidRecord() {
        assertEquals(
                "bid-records/1001/merged/credentials.md",
                StoragePath.createMergedMarkdownPath(BID_RECORD_ID));
    }
}
