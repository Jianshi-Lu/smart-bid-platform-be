package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoragePathTest {

    private static final int BID_RECORD_ID = 1001;
    private static final Long BIDDER_ACCOUNT_ID = 55L;

    @Test
    void shouldCreateDeterministicOriginalPathUnderAccountAndFileUuid() {
        String fileUuid = UUID.randomUUID().toString();

        StoragePath.OriginalFilePath first =
                StoragePath.createOriginalFilePath(
                        BIDDER_ACCOUNT_ID, fileUuid, "../营业执照.pdf");
        StoragePath.OriginalFilePath second =
                StoragePath.createOriginalFilePath(
                        BIDDER_ACCOUNT_ID, fileUuid, "../营业执照.pdf");

        assertEquals("营业执照.pdf", first.safeOriginalFilename());
        assertTrue(first.relativePath().startsWith(
                BIDDER_ACCOUNT_ID + "/files/" + fileUuid + "/original/"));
        assertTrue(first.relativePath().endsWith(fileUuid + "_营业执照.pdf"));
        assertEquals(first.relativePath(), second.relativePath());

        String otherUuid = UUID.randomUUID().toString();
        assertNotEquals(
                first.relativePath(),
                StoragePath.createOriginalFilePath(
                        BIDDER_ACCOUNT_ID, otherUuid, "../营业执照.pdf")
                        .relativePath());
    }

    @Test
    void shouldRejectOriginalPathWithoutBidderAccount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StoragePath.createOriginalFilePath(
                        null, UUID.randomUUID().toString(), "license.pdf"));
    }

    @Test
    void shouldCreateStableMergedMarkdownPathUnderAccountAndBidRecord() {
        assertEquals(
                "55/merged/1001/credentials.md",
                StoragePath.createMergedMarkdownPath(
                        BID_RECORD_ID, BIDDER_ACCOUNT_ID));
    }

    @Test
    void shouldMapOriginalObjectToFixedConvertedMarkdownPath() {
        String fileUuid = UUID.randomUUID().toString();
        String originalPath = "55/files/" + fileUuid
                + "/original/" + fileUuid + "_file.v2.pdf";

        assertEquals(
                "55/files/" + fileUuid + "/converted/content.md",
                StoragePath.createConvertedMarkdownPath(originalPath));
    }

    @Test
    void shouldRejectConvertedPathBuiltFromNonOriginalObject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StoragePath.createConvertedMarkdownPath(
                        "55/merged/1001/credentials.md"));
    }
}
