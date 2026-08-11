package io.gitee.lujianshi.bidwinningbackend.storage.vector;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.model.PreparedCredentialDocument;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** 通过独立 vector-service 持久化和删除资信文件向量。 */
@Component
public class CredentialFileVectorStorage {

    private final RestClient restClient;

    public CredentialFileVectorStorage(
            @Qualifier("vectorServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** 先清理旧 chunks，再以同一 fileUuid 替换写入一份资信 Markdown。 */
    public int replaceDocument(PreparedCredentialDocument document) {
        if (document == null) {
            throw failure("VALIDATE_REQUEST", "待向量化资信文件不能为空", null);
        }
        deleteDocument(document.fileUuid());
        VectorizeRequest request = new VectorizeRequest(
                List.of(toRequestDocument(document)));
        try {
            VectorizeResponse response = restClient.post()
                    .uri("/internal/credential-files/vectorize")
                    .body(request)
                    .retrieve()
                    .body(VectorizeResponse.class);
            int chunkCount = validateResponse(document, response);
            int actualChunkCount = getVectorStatus(document.fileUuid());
            if (actualChunkCount != chunkCount) {
                throw failure(
                        "VERIFY_VECTOR_STATUS",
                        "资信文件向量化后 Chroma chunk 数与服务回报不一致",
                        null);
            }
            return chunkCount;
        } catch (RestClientException exception) {
            deleteAfterFailure(document.fileUuid(), exception);
            throw failure("VECTOR_SERVICE_CALL", "资信文件向量化服务调用失败", exception);
        } catch (AppException exception) {
            deleteAfterFailure(document.fileUuid(), exception);
            throw exception;
        }
    }

    /** 查询 fileUuid 在私有投标资料 collection 中的实际 chunk 数。 */
    public int getVectorStatus(String fileUuid) {
        validateFileUuid(fileUuid, "VALIDATE_STATUS");
        try {
            StatusResponse response = restClient.get()
                    .uri("/internal/credential-files/{fileUuid}", fileUuid)
                    .retrieve()
                    .body(StatusResponse.class);
            if (response == null
                    || !fileUuid.equals(response.fileUuid())
                    || response.chunkCount() < 0) {
                throw failure(
                        "VALIDATE_VECTOR_RESPONSE",
                        "资信文件向量状态结果不合法",
                        null);
            }
            return response.chunkCount();
        } catch (RestClientException exception) {
            throw failure("VECTOR_SERVICE_CALL", "资信文件向量状态查询失败", exception);
        }
    }

    /** 按 fileUuid 幂等删除并验证一份资信文件的全部 chunks。 */
    public void deleteDocument(String fileUuid) {
        validateFileUuid(fileUuid, "VALIDATE_DELETE");
        try {
            DeleteResponse response = restClient.delete()
                    .uri("/internal/credential-files/{fileUuid}", fileUuid)
                    .retrieve()
                    .body(DeleteResponse.class);
            if (response == null
                    || !fileUuid.equals(response.fileUuid())
                    || response.deletedChunkCount() == null
                    || response.deletedChunkCount() < 0) {
                throw failure(
                        "VALIDATE_VECTOR_RESPONSE",
                        "资信文件向量删除结果不合法",
                        null);
            }
            if (getVectorStatus(fileUuid) != 0) {
                throw failure(
                        "VERIFY_VECTOR_DELETION",
                        "资信文件向量删除后仍存在 Chroma chunks",
                        null);
            }
        } catch (RestClientException exception) {
            throw failure("DELETE_VECTORS", "资信文件向量删除失败", exception);
        }
    }

    private int validateResponse(
            PreparedCredentialDocument document,
            VectorizeResponse response) {
        if (response == null || response.documents() == null
                || response.documents().size() != 1) {
            throw failure(
                    "VALIDATE_VECTOR_RESPONSE",
                    "资信文件向量化结果数量不一致",
                    null);
        }
        VectorizeResponse.Document result = response.documents().getFirst();
        if (result == null || !document.fileUuid().equals(result.fileUuid())) {
            throw failure(
                    "VALIDATE_VECTOR_RESPONSE",
                    "资信文件向量化结果 fileUuid 不一致",
                    null);
        }
        if (!result.success()) {
            String message = result.message() == null
                    || result.message().isBlank()
                    ? "资信文件向量化失败"
                    : result.message();
            throw failure("VECTOR_SERVICE_REJECTED", message, null);
        }
        if (result.chunkCount() == null || result.chunkCount() <= 0) {
            throw failure(
                    "VALIDATE_VECTOR_RESPONSE",
                    "资信文件向量化 chunk 数不合法",
                    null);
        }
        return result.chunkCount();
    }

    private VectorizeRequest.Document toRequestDocument(
            PreparedCredentialDocument document) {
        return new VectorizeRequest.Document(
                document.fileUuid(),
                document.markdownText(),
                document.credentialFileId(),
                document.bidRecordId(),
                document.originalFilename(),
                document.originalObjectKey(),
                document.convertedObjectKey(),
                document.originalSha256(),
                document.convertedSha256());
    }

    private void deleteAfterFailure(String fileUuid, Throwable original) {
        try {
            deleteDocument(fileUuid);
        } catch (RuntimeException cleanupFailure) {
            if (original != null) {
                original.addSuppressed(cleanupFailure);
            }
        }
    }

    private void validateFileUuid(String fileUuid, String stage) {
        if (fileUuid == null || fileUuid.isBlank()) {
            throw failure(stage, "fileUuid 不能为空", null);
        }
    }

    private AppException failure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_VECTORIZATION_FAILED,
                "credential-file",
                "VECTORIZE",
                stage,
                message,
                cause);
    }

    private record VectorizeRequest(List<Document> documents) {
        private record Document(
                String fileUuid,
                String content,
                Long credentialFileId,
                Integer bidRecordId,
                String originalFilename,
                String originalObjectKey,
                String convertedObjectKey,
                String originalSha256,
                String convertedSha256) {
        }
    }

    private record VectorizeResponse(List<Document> documents) {
        private record Document(
                String fileUuid,
                boolean success,
                Integer chunkCount,
                String message) {
        }
    }

    private record StatusResponse(String fileUuid, int chunkCount) {
    }

    private record DeleteResponse(String fileUuid, Integer deletedChunkCount) {
    }
}
