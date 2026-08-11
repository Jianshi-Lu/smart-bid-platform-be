package io.gitee.lujianshi.bidwinningbackend.storage.vector;

import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.PreparedKnowledgeDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** 通过独立 vector-service 持久化和删除公共知识文件向量。 */
@Component
public class KnowledgeFileVectorStorage {

    private final RestClient restClient;

    public KnowledgeFileVectorStorage(
            @Qualifier("vectorServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** 先清理旧 chunks，再按 documentUuid 替换写入知识 Markdown。 */
    public int vectorizeDocument(PreparedKnowledgeDocument document) {
        if (document == null) {
            throw failure("VALIDATE_REQUEST", "待向量化知识文件不能为空", null);
        }
        deleteDocument(document.documentUuid());
        VectorizeRequest request = new VectorizeRequest(
                List.of(toRequestDocument(document)));
        try {
            VectorizeResponse response = restClient.post()
                    .uri("/internal/knowledge-files/vectorize")
                    .body(request)
                    .retrieve()
                    .body(VectorizeResponse.class);
            int chunkCount = validateResponse(document, response);
            int actualChunkCount = getVectorStatus(document.documentUuid());
            if (actualChunkCount != chunkCount) {
                throw failure(
                        "VERIFY_VECTOR_STATUS",
                        "知识文件向量化后 Chroma chunk 数与服务回报不一致",
                        null);
            }
            return chunkCount;
        } catch (RestClientException exception) {
            deleteAfterFailure(document.documentUuid(), exception);
            throw failure("VECTOR_SERVICE_CALL", "知识文件向量化服务调用失败", exception);
        } catch (AppException exception) {
            deleteAfterFailure(document.documentUuid(), exception);
            throw exception;
        }
    }

    /** 查询 documentUuid 当前实际存在的 chunk 数。 */
    public Integer getVectorStatus(String documentUuid) {
        validateDocumentUuid(documentUuid, "VALIDATE_STATUS");
        try {
            StatusResponse response = restClient.get()
                    .uri("/internal/knowledge-files/{documentUuid}", documentUuid)
                    .retrieve()
                    .body(StatusResponse.class);
            if (response == null
                    || !documentUuid.equals(response.documentUuid())
                    || response.chunkCount() < 0) {
                throw failure(
                        "VALIDATE_VECTOR_RESPONSE",
                        "知识文件向量状态结果不合法",
                        null);
            }
            return response.chunkCount();
        } catch (RestClientException exception) {
            throw failure("VECTOR_SERVICE_CALL", "知识文件向量状态查询失败", exception);
        }
    }

    /** 按 documentUuid 幂等删除并读后验证全部 chunks 已清理。 */
    public void deleteDocument(String documentUuid) {
        validateDocumentUuid(documentUuid, "VALIDATE_DELETE");
        try {
            DeleteResponse response = restClient.delete()
                    .uri("/internal/knowledge-files/{documentUuid}", documentUuid)
                    .retrieve()
                    .body(DeleteResponse.class);
            if (response == null
                    || !documentUuid.equals(response.documentUuid())
                    || response.deletedChunkCount() == null
                    || response.deletedChunkCount() < 0) {
                throw failure(
                        "VALIDATE_VECTOR_RESPONSE",
                        "知识文件向量删除结果不合法",
                        null);
            }
            if (getVectorStatus(documentUuid) != 0) {
                throw failure(
                        "VERIFY_VECTOR_DELETION",
                        "知识文件向量删除后仍存在 Chroma chunks",
                        null);
            }
        } catch (RestClientException exception) {
            throw failure("DELETE_VECTORS", "知识文件向量删除失败", exception);
        }
    }

    private int validateResponse(
            PreparedKnowledgeDocument document,
            VectorizeResponse response) {
        if (response == null || response.documents() == null
                || response.documents().size() != 1) {
            throw failure(
                    "VALIDATE_VECTOR_RESPONSE",
                    "知识文件向量化结果数量不一致",
                    null);
        }
        VectorizeResponse.Document result = response.documents().getFirst();
        if (result == null || !document.documentUuid().equals(
                result.documentUuid())) {
            throw failure(
                    "VALIDATE_VECTOR_RESPONSE",
                    "知识文件向量化结果 documentUuid 不一致",
                    null);
        }
        if (!result.success()) {
            String message = result.message() == null
                    || result.message().isBlank()
                    ? "知识文件向量化失败"
                    : result.message();
            throw failure("VECTOR_SERVICE_REJECTED", message, null);
        }
        if (result.chunkCount() == null || result.chunkCount() <= 0) {
            throw failure(
                    "VALIDATE_VECTOR_RESPONSE",
                    "知识文件向量化 chunk 数不合法",
                    null);
        }
        return result.chunkCount();
    }

    private VectorizeRequest.Document toRequestDocument(
            PreparedKnowledgeDocument document) {
        return new VectorizeRequest.Document(
                document.documentUuid(),
                document.markdownText(),
                document.originalFilename(),
                document.originalObjectKey(),
                document.convertedObjectKey(),
                document.originalSha256(),
                document.convertedSha256());
    }

    private void deleteAfterFailure(String documentUuid, Throwable original) {
        try {
            deleteDocument(documentUuid);
        } catch (RuntimeException cleanupFailure) {
            if (original != null) {
                original.addSuppressed(cleanupFailure);
            }
        }
    }

    private void validateDocumentUuid(String documentUuid, String stage) {
        if (documentUuid == null || documentUuid.isBlank()) {
            throw failure(stage, "documentUuid 不能为空", null);
        }
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

    private record VectorizeRequest(List<Document> documents) {
        private record Document(
                String documentUuid,
                String content,
                String originalFilename,
                String originalObjectKey,
                String convertedObjectKey,
                String originalSha256,
                String convertedSha256) {
        }
    }

    private record VectorizeResponse(List<Document> documents) {
        private record Document(
                String documentUuid,
                boolean success,
                Integer chunkCount,
                String message) {
        }
    }

    private record StatusResponse(String documentUuid, int chunkCount) {
    }

    private record DeleteResponse(
            String documentUuid,
            Integer deletedChunkCount) {
    }
}
