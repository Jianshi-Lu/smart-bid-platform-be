package io.gitee.lujianshi.bidwinningbackend.knowledgefile;

import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.KnowledgeObjectKeyGenerator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeObjectKeyGeneratorTest {

    @Test
    void shouldUseDocumentUuidAsRootWithoutKnowledgeTypePrefix() {
        KnowledgeObjectKeyGenerator generator = new KnowledgeObjectKeyGenerator();
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");

        var original = generator.original(
                documentUuid,
                "C:\\fakepath\\采购规则.txt");

        assertEquals("采购规则.txt", original.safeOriginalFilename());
        assertEquals(
                documentUuid + "/original/采购规则.txt",
                original.objectKey());
        assertEquals(
                documentUuid + "/converted/content.md",
                generator.converted(documentUuid));
    }
}
