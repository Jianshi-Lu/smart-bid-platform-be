package io.gitee.lujianshi.bidwinningbackend.knowledgefile.client;

import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto.ChatAgentVectorStatusResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto.ChatAgentVectorizeRequest;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto.ChatAgentVectorizeResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.PreparedKnowledgeDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** Java 调用 chat-agent 知识文件向量化接口的唯一客户端。 */
@Component
public class ChatAgentIngestionClient {

    private final RestClient restClient;

    public ChatAgentIngestionClient(
            @Qualifier("chatAgentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 提交单个 converted Markdown 给 chat-agent 向量化，并严格校验结果。
     *
     * <p>单文件调用便于知识文件按 documentUuid 独立落库：
     * 一个文件失败只影响该文件的状态，不连坐同批其他文件。</p>
     *
     * @param document 已完成 original/converted 持久化的稳定文档
     * @return chat-agent 实际写入 Chroma 的 chunk 数
     */
    public int vectorizeDocument(PreparedKnowledgeDocument document) {
        if (document == null) {
            throw failure("VALIDATE_REQUEST", "待向量化知识文件不能为空", null);
        }

        ChatAgentVectorizeRequest request = new ChatAgentVectorizeRequest(
                List.of(toRequestDocument(document)));
        try {
            ChatAgentVectorizeResponse response = restClient.post()
                    .uri("/internal/knowledge-files/vectorize")
                    .body(request)
                    .retrieve()
                    .body(ChatAgentVectorizeResponse.class);
            if (response == null || response.documents() == null
                    || response.documents().size() != 1) {
                throw failure(
                        "VALIDATE_PYTHON_RESPONSE",
                        "知识文件向量化结果数量不一致",
                        null);
            }

            ChatAgentVectorizeResponse.Document result = response.documents().getFirst();
            if (result == null || !document.documentUuid().equals(
                    result.documentUuid())) {
                throw failure(
                        "VALIDATE_PYTHON_RESPONSE",
                        "知识文件向量化结果 document_uuid 不一致",
                        null);
            }
            if (!result.success()) {
                String message = result.message() == null
                        || result.message().isBlank()
                        ? "知识文件向量化失败"
                        : result.message();
                throw failure("PYTHON_REJECTED", message, null);
            }
            if (result.chunkCount() == null || result.chunkCount() <= 0) {
                throw failure(
                        "VALIDATE_PYTHON_RESPONSE",
                        "知识文件向量化 chunk 数不合法",
                        null);
            }
            return result.chunkCount();
        } catch (RestClientException exception) {
            throw failure("PYTHON_CALL", "知识文件向量化服务调用失败", exception);
        }
    }

    /**
     * 查询当前 Chroma 中指定 documentUuid 的 chunk 数（不存在为 0）。
     *
     * <p>用于重试前对账：向量已存在时直接置 VECTORIZED，避免重复向量化；
     * 也用于重启后校验数据库状态与向量库是否一致。</p>
     */
    public Integer getVectorStatus(String documentUuid) {
        if (documentUuid == null || documentUuid.isBlank()) {
            throw failure("VALIDATE_STATUS", "document_uuid 不能为空", null);
        }
        try {
            ChatAgentVectorStatusResponse response = restClient.get()
                    .uri("/internal/knowledge-files/{documentUuid}", documentUuid)
                    .retrieve()
                    .body(ChatAgentVectorStatusResponse.class);
            if (response == null || !documentUuid.equals(response.documentUuid())) {
                throw failure(
                        "VALIDATE_PYTHON_RESPONSE",
                        "知识文件向量状态结果不合法",
                        null);
            }
            return response.chunkCount();
        } catch (RestClientException exception) {
            throw failure("PYTHON_CALL", "知识文件向量状态查询失败", exception);
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

    private ChatAgentVectorizeRequest.Document toRequestDocument(
            PreparedKnowledgeDocument document) {
        return new ChatAgentVectorizeRequest.Document(
                document.documentUuid(),
                document.markdownText(),
                document.originalFilename(),
                document.originalObjectKey(),
                document.convertedObjectKey(),
                document.originalSha256(),
                document.convertedSha256());
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
