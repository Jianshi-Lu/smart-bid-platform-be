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
    void shouldVectorizeAndDeleteByStableDocumentUuid() {
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
                        {"documents":[{"documentUuid":"%s","chunkCount":2}]}
                        """.formatted(documentUuid),
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        "http://127.0.0.1:8082/internal/knowledge-files/"
                                + documentUuid))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        var response = client.vectorizeDocuments(List.of(document));
        client.deleteDocument(documentUuid);

        assertEquals(1, response.size());
        assertEquals(documentUuid, response.getFirst().documentUuid());
        assertEquals(2, response.getFirst().chunkCount());
        server.verify();
    }
}
