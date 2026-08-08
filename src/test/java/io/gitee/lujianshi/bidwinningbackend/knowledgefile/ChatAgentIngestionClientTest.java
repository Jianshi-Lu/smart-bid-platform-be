package io.gitee.lujianshi.bidwinningbackend.knowledgefile;

import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.ChatAgentIngestionClient;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.PreparedKnowledgeDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChatAgentIngestionClientTest {

    @Test
    void shouldVectorizeQueryStatusAndDeleteByStableDocumentUuid() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChatAgentIngestionClient client = new ChatAgentIngestionClient(
                builder.baseUrl("http://127.0.0.1:8082").build());
        String documentUuid = "550e8400-e29b-41d4-a716-446655440000";
        PreparedKnowledgeDocument document = new PreparedKnowledgeDocument(
                documentUuid,
                "knowledge.txt",
                documentUuid + "/original/knowledge.txt",
                documentUuid + "/converted/content.md",
                "a".repeat(64),
                "b".repeat(64),
                "# 唯一知识".getBytes(StandardCharsets.UTF_8));

        server.expect(once(), requestTo(
                        "http://127.0.0.1:8082/internal/knowledge-files/vectorize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "documents": [{
                            "documentUuid": "%s",
                            "content": "# 唯一知识",
                            "originalFilename": "knowledge.txt",
                            "originalObjectKey": "%s/original/knowledge.txt",
                            "convertedObjectKey": "%s/converted/content.md",
                            "originalSha256": "%s",
                            "convertedSha256": "%s"
                          }]
                        }
                        """.formatted(
                        documentUuid,
                        documentUuid,
                        documentUuid,
                        "a".repeat(64),
                        "b".repeat(64))))
                .andRespond(withSuccess(
                        """
                        {"documents":[{"documentUuid":"%s","success":true,"chunkCount":2,"message":null}]}
                        """.formatted(documentUuid),
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        "http://127.0.0.1:8082/internal/knowledge-files/"
                                + documentUuid))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"documentUuid":"%s","chunkCount":2}
                        """.formatted(documentUuid),
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        "http://127.0.0.1:8082/internal/knowledge-files/"
                                + documentUuid))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        int chunkCount = client.vectorizeDocument(document);
        Integer statusChunkCount = client.getVectorStatus(documentUuid);
        client.deleteDocument(documentUuid);

        assertEquals(2, chunkCount);
        assertEquals(2, statusChunkCount);
        server.verify();
    }

    @Test
    void shouldRejectDocumentWhenPythonReportsFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChatAgentIngestionClient client = new ChatAgentIngestionClient(
                builder.baseUrl("http://127.0.0.1:8082").build());
        String documentUuid = "550e8400-e29b-41d4-a716-446655440000";
        PreparedKnowledgeDocument document = new PreparedKnowledgeDocument(
                documentUuid,
                "knowledge.txt",
                documentUuid + "/original/knowledge.txt",
                documentUuid + "/converted/content.md",
                "a".repeat(64),
                "b".repeat(64),
                "# 唯一知识".getBytes(StandardCharsets.UTF_8));

        server.expect(once(), requestTo(
                        "http://127.0.0.1:8082/internal/knowledge-files/vectorize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"documents":[{"documentUuid":"%s","success":false,"chunkCount":null,"message":"切分失败"}]}
                        """.formatted(documentUuid),
                        MediaType.APPLICATION_JSON));

        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                io.gitee.lujianshi.bidwinningbackend.exception.AppException.class,
                () -> client.vectorizeDocument(document));

        org.junit.jupiter.api.Assertions.assertEquals(
                "切分失败", exception.getMessage());
        server.verify();
    }
}
