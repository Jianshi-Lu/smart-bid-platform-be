package io.gitee.lujianshi.bidwinningbackend.knowledgefile;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.ToMarkdownClient;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.ChatAgentIngestionClient;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto.ChatAgentVectorizeResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.DocumentUuidGenerator;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.KnowledgeObjectKeyGenerator;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.PreparedKnowledgeDocument;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.service.KnowledgeFileIngestionService;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.service.KnowledgeFileObjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeFileIngestionServiceTest {

    @TempDir
    Path storageRoot;

    @Test
    void shouldStoreConvertAndVectorizeKnowledgeFile() throws Exception {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        byte[] original = "原始知识".getBytes(StandardCharsets.UTF_8);
        byte[] markdown = "# 测试知识\n\n唯一事实。".getBytes(StandardCharsets.UTF_8);
        when(fixture.toMarkdownClient().convert(
                any(), eq("knowledge.txt"), eq("text/plain"),
                eq((long) original.length)))
                .thenReturn(new PythonMarkdownResponse(
                        markdown, "knowledge.md", "text/markdown"));
        when(fixture.chatAgentClient().vectorizeDocuments(anyList()))
                .thenAnswer(invocation -> {
                    List<PreparedKnowledgeDocument> documents = invocation.getArgument(0);
                    return List.of(new ChatAgentVectorizeResponse.Document(
                            documents.getFirst().documentUuid(), 2));
                });

        var response = fixture.service().ingest(List.of(upload(
                "knowledge.txt", original)));

        assertEquals(1, response.documents().size());
        assertEquals(documentUuid.toString(), response.documents().getFirst().documentUuid());
        assertEquals(
                documentUuid + "/original/knowledge.txt",
                response.documents().getFirst().originalObjectKey());
        assertEquals(
                documentUuid + "/converted/content.md",
                response.documents().getFirst().convertedObjectKey());
        assertEquals(2, response.documents().getFirst().chunkCount());
        try (var input = fixture.storage().open(
                response.documents().getFirst().convertedObjectKey())) {
            assertEquals(
                    new String(markdown, StandardCharsets.UTF_8),
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void shouldDeleteAllObjectsAndVectorsWhenVectorizationFails() {
        UUID firstUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        UUID secondUuid = UUID.fromString(
                "660e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(firstUuid, secondUuid);
        byte[] markdown = "# 回滚测试".getBytes(StandardCharsets.UTF_8);
        when(fixture.toMarkdownClient().convert(
                any(), any(), eq("text/plain"), anyLong()))
                .thenReturn(new PythonMarkdownResponse(
                        markdown, "converted.md", "text/markdown"));
        when(fixture.chatAgentClient().vectorizeDocuments(anyList()))
                .thenThrow(new AppException(
                        AppErrorCode.KNOWLEDGE_FILE_VECTORIZATION_FAILED,
                        "knowledge-file",
                        "VECTORIZE",
                        "PYTHON_CALL",
                        "向量化失败"));

        assertThrows(AppException.class, () -> fixture.service().ingest(List.of(
                upload("first.txt", "第一份知识".getBytes(StandardCharsets.UTF_8)),
                upload("second.txt", "第二份知识".getBytes(StandardCharsets.UTF_8)))));

        for (UUID documentUuid : List.of(firstUuid, secondUuid)) {
            verify(fixture.chatAgentClient()).deleteDocument(documentUuid.toString());
            assertMissing(fixture.storage(),
                    documentUuid + "/original/"
                            + (documentUuid.equals(firstUuid) ? "first.txt" : "second.txt"));
            assertMissing(fixture.storage(),
                    documentUuid + "/converted/content.md");
        }
    }

    private TestFixture fixture(UUID... uuids) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getLocal().setRoot(storageRoot.toString());
        LocalFileStorage storage = new LocalFileStorage(properties);
        KnowledgeFileObjectService objectService = new KnowledgeFileObjectService(
                storage, new Sha256FileChecksumCalculator());
        ToMarkdownClient toMarkdownClient = mock(ToMarkdownClient.class);
        ChatAgentIngestionClient chatAgentClient = mock(ChatAgentIngestionClient.class);
        DocumentUuidGenerator uuidGenerator = mock(DocumentUuidGenerator.class);
        AtomicInteger uuidIndex = new AtomicInteger();
        when(uuidGenerator.generate()).thenAnswer(
                ignored -> uuids[uuidIndex.getAndIncrement()]);

        return new TestFixture(
                storage,
                toMarkdownClient,
                chatAgentClient,
                new KnowledgeFileIngestionService(
                        uuidGenerator,
                        new KnowledgeObjectKeyGenerator(),
                        objectService,
                        toMarkdownClient,
                        chatAgentClient));
    }

    private KnowledgeFileUpload upload(String filename, byte[] content) {
        return new KnowledgeFileUpload(
                filename,
                "text/plain",
                content.length,
                () -> new ByteArrayInputStream(content));
    }

    private void assertMissing(LocalFileStorage storage, String objectKey) {
        assertThrows(IOException.class, () -> storage.open(objectKey));
    }

    private record TestFixture(
            LocalFileStorage storage,
            ToMarkdownClient toMarkdownClient,
            ChatAgentIngestionClient chatAgentClient,
            KnowledgeFileIngestionService service) {
    }
}
