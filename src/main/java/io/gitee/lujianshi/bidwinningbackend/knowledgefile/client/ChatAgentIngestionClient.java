package io.gitee.lujianshi.bidwinningbackend.knowledgefile.client;

import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto.ChatAgentVectorizeRequest;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto.ChatAgentVectorizeResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.PreparedKnowledgeDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Java 调用 chat-agent 知识文件向量化接口的唯一客户端。 */
@Component
public class ChatAgentIngestionClient {

    private final RestClient restClient;

    public ChatAgentIngestionClient(
            @Qualifier("chatAgentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** 批量提交 converted Markdown，并严格校验逐文档结果。 */
    public List<ChatAgentVectorizeResponse.Document> vectorizeDocuments(
            List<PreparedKnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            throw failure("VALIDATE_REQUEST", "待向量化知识文件不能为空", null);
        }

        ChatAgentVectorizeRequest request = new ChatAgentVectorizeRequest(
                documents.stream()
                        .map(document -> new ChatAgentVectorizeRequest.Document(
                                document.documentUuid(),
                                document.markdownText(),
                                document.originalFilename(),
                                document.originalObjectKey(),
                                document.convertedObjectKey(),
                                document.originalSha256(),
                                document.convertedSha256()))
                        .toList());

        try {
            ChatAgentVectorizeResponse response = restClient.post()
                    .uri("/internal/knowledge-files/vectorize")
                    .body(request)
                    .retrieve()
                    .body(ChatAgentVectorizeResponse.class);
            return validateResponse(documents, response);
        } catch (RestClientException exception) {
            throw failure("PYTHON_CALL", "知识文件向量化服务调用失败", exception);
        }
    }

    /** 按 document_uuid 幂等删除一个知识文件的全部 Chroma chunks。 */
    public void deleteDocument(String documentUuid) {
        if (documentUuid == null || documentUuid.isBlank()) {
            throw failure("VALIDATE_DELETE", "document_uuid 不能为空", null);
        }
        try {
            restClient.delete()
                    .uri("/internal/knowledge-files/{documentUuid}", documentUuid)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw failure("DELETE_VECTORS", "知识文件向量删除失败", exception);
        }
    }

    private List<ChatAgentVectorizeResponse.Document> validateResponse(
            List<PreparedKnowledgeDocument> requests,
            ChatAgentVectorizeResponse response) {
        if (response == null || response.documents() == null
                || response.documents().size() != requests.size()) {
            throw failure(
                    "VALIDATE_PYTHON_RESPONSE",
                    "知识文件向量化结果数量不一致",
                    null);
        }

        Set<String> expectedUuids = requests.stream()
                .map(PreparedKnowledgeDocument::documentUuid)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> actualUuids = new HashSet<>();
        for (ChatAgentVectorizeResponse.Document document : response.documents()) {
            if (document == null || document.documentUuid() == null
                    || document.documentUuid().isBlank()
                    || document.chunkCount() <= 0
                    || !actualUuids.add(document.documentUuid())) {
                throw failure(
                        "VALIDATE_PYTHON_RESPONSE",
                        "知识文件向量化结果不合法",
                        null);
            }
        }
        if (!expectedUuids.equals(actualUuids)) {
            throw failure(
                    "VALIDATE_PYTHON_RESPONSE",
                    "知识文件向量化结果 document_uuid 不一致",
                    null);
        }
        return List.copyOf(response.documents());
    }

    private AppException failure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.KNOWLEDGE_FILE_VECTORIZATION_FAILED,
                "knowledge-file",
                "VECTORIZE",
                stage,
                message,
                cause);
    }
}
