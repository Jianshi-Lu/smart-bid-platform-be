package io.gitee.lujianshi.bidwinningbackend.credentialfile.intake;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadIntakeModelTest {

    @Test
    void shouldAllowPendingItemContentToOpenIndependentStreams() throws Exception {
        byte[] content = "credential-content".getBytes(StandardCharsets.UTF_8);
        UploadItem.Content itemContent =
                () -> new ByteArrayInputStream(content);

        try (var first = itemContent.openStream();
             var second = itemContent.openStream()) {
            assertArrayEquals(content, first.readAllBytes());
            assertArrayEquals(content, second.readAllBytes());
        }
    }

    @Test
    void shouldValidatePendingItemAndResolvedContextBoundaries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> item("empty.pdf", ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UploadFileContext(
                        0, 55L, "bidder01", UUID.randomUUID().toString()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UploadFileContext(
                        1001, null, "bidder01", UUID.randomUUID().toString()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UploadFileContext(
                        1001, 55L, "bidder01", "../invalid"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UploadFileContext(
                        1001, 55L, " ", UUID.randomUUID().toString()));

        String fileUuid = UUID.randomUUID().toString();
        UploadFileContext context = new UploadFileContext(
                1001, 55L, "bidder01", fileUuid);
        assertEquals(fileUuid, context.fileUuid());
        assertEquals(55L, context.bidderAccountId());
        assertEquals("bidder01", context.username());
    }

    private UploadItem item(String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new UploadItem(
                filename,
                "application/pdf",
                bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }
}
