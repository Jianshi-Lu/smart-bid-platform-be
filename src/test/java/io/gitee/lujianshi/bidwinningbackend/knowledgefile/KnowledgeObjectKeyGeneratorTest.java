package io.gitee.lujianshi.bidwinningbackend.knowledgefile;

import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.KnowledgeObjectKeyGenerator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeObjectKeyGeneratorTest {

    @Test
    void shouldUseUsernameAndFilenameAsRootWithoutKnowledgeTypePrefix() {
        KnowledgeObjectKeyGenerator generator = new KnowledgeObjectKeyGenerator();
        String username = "admin01";
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");

        var original = generator.original(
                username,
                documentUuid,
                "C:\\fakepath\\采购规则.txt");

        assertEquals("采购规则.txt", original.safeOriginalFilename());
        assertEquals(
                username + "/采购规则.txt/original/"
                        + documentUuid + "_采购规则.txt",
                original.objectKey());
        assertEquals(
                username + "/采购规则.txt/converted/采购规则.md",
                generator.converted(
                        username, "采购规则.txt", "采购规则.md"));
    }
}
