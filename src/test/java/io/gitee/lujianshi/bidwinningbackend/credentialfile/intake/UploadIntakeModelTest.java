package io.gitee.lujianshi.bidwinningbackend.credentialfile.intake;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    void shouldCreateImmutableUploadBatchSnapshot() {
        List<UploadItem> source = new ArrayList<>();
        source.add(item("license.pdf", "content"));

        UploadBatch batch = new UploadBatch(1001, source);
        source.add(item("other.pdf", "other"));

        assertEquals(1, batch.items().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> batch.items().add(item("third.pdf", "third")));
    }

    @Test
    void shouldValidatePendingItemAndResolvedContextBoundaries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> item("empty.pdf", ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UploadBatch(0, List.of(item("file.pdf", "content"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UploadBatchContext(1001, "../invalid"));

        String batchId = UUID.randomUUID().toString();
        UploadBatchContext context = new UploadBatchContext(1001, batchId);
        assertEquals(batchId, context.uploadBatchId());
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
