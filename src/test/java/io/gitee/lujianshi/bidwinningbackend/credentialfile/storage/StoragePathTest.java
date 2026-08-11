package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoragePathTest {

    private static final int BID_RECORD_ID = 1001;
    private static final String USERNAME = "bidder01";

    @Test
    void shouldCreateDeterministicOriginalPathUnderUsernameAndFilename() {
        String fileUuid = UUID.randomUUID().toString();

        StoragePath.OriginalFilePath first =
                StoragePath.createOriginalFilePath(
                        USERNAME, fileUuid, "../营业执照.pdf");
        StoragePath.OriginalFilePath second =
                StoragePath.createOriginalFilePath(
                        USERNAME, fileUuid, "../营业执照.pdf");

        assertEquals("营业执照.pdf", first.safeOriginalFilename());
        assertTrue(first.relativePath().startsWith(
                USERNAME + "/营业执照.pdf/original/"));
        assertTrue(first.relativePath().endsWith(fileUuid + "_营业执照.pdf"));
        assertEquals(first.relativePath(), second.relativePath());

        String otherUuid = UUID.randomUUID().toString();
        assertNotEquals(
                first.relativePath(),
                StoragePath.createOriginalFilePath(
                        USERNAME, otherUuid, "../营业执照.pdf")
                        .relativePath());
    }

    @Test
    void shouldRejectOriginalPathWithoutUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StoragePath.createOriginalFilePath(
                        null, UUID.randomUUID().toString(), "license.pdf"));
    }

    @Test
    void shouldCreateStableMergedMarkdownPathUnderUsernameAndBidRecord() {
        assertEquals(
                "bidder01/merged/1001/credentials.md",
                StoragePath.createMergedMarkdownPath(
                        BID_RECORD_ID, USERNAME));
    }

    @Test
    void shouldMapOriginalObjectToConvertedMarkdownPathWithSameFilename() {
        String fileUuid = UUID.randomUUID().toString();
        String originalPath = "bidder01/file.v2.pdf/original/"
                + fileUuid + "_file.v2.pdf";

        assertEquals(
                "bidder01/file.v2.pdf/converted/" + fileUuid + "_file.v2.md",
                StoragePath.createConvertedMarkdownPath(originalPath));
        assertEquals(
                "bidder01/file.v2.pdf/converted/" + fileUuid + "_source.md",
                StoragePath.createConvertedMarkdownPath(
                        originalPath, "source.md"));

        String otherUuid = UUID.randomUUID().toString();
        assertNotEquals(
                StoragePath.createConvertedMarkdownPath(originalPath, "source.md"),
                StoragePath.createConvertedMarkdownPath(
                        "bidder01/file.v2.pdf/original/"
                                + otherUuid + "_file.v2.pdf",
                        "source.md"));
    }

    @Test
    void shouldRejectConvertedPathBuiltFromNonOriginalObject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StoragePath.createConvertedMarkdownPath(
                        "bidder01/merged/1001/credentials.md"));
    }
}
