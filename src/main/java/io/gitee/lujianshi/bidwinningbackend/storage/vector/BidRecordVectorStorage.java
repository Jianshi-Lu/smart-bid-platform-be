package io.gitee.lujianshi.bidwinningbackend.storage.vector;

import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.storage.vector.model.PreparedBidRecordDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** 通过独立 vector-service 持久化结构化投标记录及其资料向量。 */
@Component
public class BidRecordVectorStorage {

    private final RestClient restClient;

    public BidRecordVectorStorage(
            @Qualifier("vectorServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** 以稳定 {@code bid-record:{id}} 身份替换一条结构化投标记录。 */
    public int replaceDocument(PreparedBidRecordDocument document) {
        if (document == null || document.bidRecordId() == null
                || document.bidRecordId() <= 0) {
            throw failure("VALIDATE_REQUEST", "待向量化投标记录不能为空", null);
        }
        deleteDocument(document.bidRecordId());
        VectorizeRequest request = new VectorizeRequest(List.of(
                new VectorizeRequest.Document(
                        document.bidRecordId(),
                        document.content(),
                        document.invitationId(),
                        document.bidderName(),
                        document.bidNo(),
                        document.bidderAccountId(),
                        document.projectNo(),
                        document.purchaseTaskNo(),
                        document.bidAt(),
                        document.quotePrice())));
        try {
            VectorizeResponse response = restClient.post()
                    .uri("/internal/bid-records/vectorize")
                    .body(request)
                    .retrieve()
                    .body(VectorizeResponse.class);
            validateResponse(document.bidRecordId(), response);
            int actualCount = getDocumentStatus(document.bidRecordId());
            if (actualCount != 1) {
                throw failure(
                        "VERIFY_VECTOR_STATUS",
                        "投标记录向量化后文档数量不一致",
                        null);
            }
            return actualCount;
        } catch (RestClientException exception) {
            Integer reconciled = reconcileResponseLoss(document.bidRecordId());
            if (Integer.valueOf(1).equals(reconciled)) {
                return 1;
            }
            deleteAfterFailure(document.bidRecordId(), exception);
            throw failure("VECTOR_SERVICE_CALL", "投标记录向量化服务调用失败", exception);
        } catch (AppException exception) {
            deleteAfterFailure(document.bidRecordId(), exception);
            throw exception;
        }
    }

    /** 查询结构化投标记录在私有 collection 中的实际文档数。 */
    public int getDocumentStatus(Integer bidRecordId) {
        validateBidRecordId(bidRecordId, "VALIDATE_STATUS");
        try {
            StatusResponse response = restClient.get()
                    .uri("/internal/bid-records/{bidRecordId}", bidRecordId)
                    .retrieve()
                    .body(StatusResponse.class);
            return validateStatusResponse(bidRecordId, response);
        } catch (RestClientException exception) {
            throw failure("VECTOR_SERVICE_CALL", "投标记录向量状态查询失败", exception);
        }
    }

    /** 查询一条投标记录及其全部资信文件在私有 collection 中的总 chunk 数。 */
    public int getMaterialsStatus(Integer bidRecordId) {
        validateBidRecordId(bidRecordId, "VALIDATE_MATERIALS_STATUS");
        try {
            StatusResponse response = restClient.get()
                    .uri("/internal/bid-records/{bidRecordId}/materials", bidRecordId)
                    .retrieve()
                    .body(StatusResponse.class);
            return validateStatusResponse(bidRecordId, response);
        } catch (RestClientException exception) {
            throw failure("VECTOR_SERVICE_CALL", "投标资料向量状态查询失败", exception);
        }
    }

    /** 只删除结构化投标记录文档，保留其资信文件 chunks。 */
    public void deleteDocument(Integer bidRecordId) {
        deleteAndVerify(
                bidRecordId,
                "/internal/bid-records/{bidRecordId}/document",
                false);
    }

    /** 删除结构化投标记录与其全部资信文件 chunks。 */
    public void deleteAllMaterials(Integer bidRecordId) {
        deleteAndVerify(
                bidRecordId,
                "/internal/bid-records/{bidRecordId}",
                true);
    }

    private void deleteAndVerify(
            Integer bidRecordId,
            String uri,
            boolean allMaterials) {
        validateBidRecordId(bidRecordId, "VALIDATE_DELETE");
        try {
            DeleteResponse response = restClient.delete()
                    .uri(uri, bidRecordId)
                    .retrieve()
                    .body(DeleteResponse.class);
            if (response == null
                    || !bidRecordId.equals(response.bidRecordId())
                    || response.deletedChunkCount() == null
                    || response.deletedChunkCount() < 0) {
                throw failure(
                        "VALIDATE_VECTOR_RESPONSE",
                        "投标资料向量删除结果不合法",
                        null);
            }
            int remaining = allMaterials
                    ? getMaterialsStatus(bidRecordId)
                    : getDocumentStatus(bidRecordId);
            if (remaining != 0) {
                throw failure(
                        "VERIFY_VECTOR_DELETION",
                        "投标资料向量删除后仍存在文档",
                        null);
            }
        } catch (RestClientException exception) {
            throw failure("DELETE_VECTORS", "投标资料向量删除失败", exception);
        }
    }

    private void validateResponse(
            Integer bidRecordId,
            VectorizeResponse response) {
        if (response == null || response.documents() == null
                || response.documents().size() != 1) {
            throw failure(
                    "VALIDATE_VECTOR_RESPONSE",
                    "投标记录向量化结果数量不一致",
                    null);
        }
        VectorizeResponse.Document result = response.documents().getFirst();
        if (result == null || !bidRecordId.equals(result.bidRecordId())) {
            throw failure(
                    "VALIDATE_VECTOR_RESPONSE",
                    "投标记录向量化结果 ID 不一致",
                    null);
        }
        if (!result.success()) {
            String message = result.message() == null || result.message().isBlank()
                    ? "投标记录向量化失败"
                    : result.message();
            throw failure("VECTOR_SERVICE_REJECTED", message, null);
        }
        if (result.chunkCount() == null || result.chunkCount() != 1) {
            throw failure(
                    "VALIDATE_VECTOR_RESPONSE",
                    "结构化投标记录必须且只能写入一个文档",
                    null);
        }
    }

    private int validateStatusResponse(
            Integer bidRecordId,
            StatusResponse response) {
        if (response == null
                || !bidRecordId.equals(response.bidRecordId())
                || response.chunkCount() < 0) {
            throw failure(
                    "VALIDATE_VECTOR_RESPONSE",
                    "投标资料向量状态结果不合法",
                    null);
        }
        return response.chunkCount();
    }

    private Integer reconcileResponseLoss(Integer bidRecordId) {
        try {
            return getDocumentStatus(bidRecordId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void deleteAfterFailure(Integer bidRecordId, Throwable original) {
        try {
            deleteDocument(bidRecordId);
        } catch (RuntimeException cleanupFailure) {
            if (original != null) {
                original.addSuppressed(cleanupFailure);
            }
        }
    }

    private void validateBidRecordId(Integer bidRecordId, String stage) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw failure(stage, "bidRecordId 必须是正整数", null);
        }
    }

    private AppException failure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.BID_RECORD_VECTORIZATION_FAILED,
                "bid-record",
                "VECTORIZE",
                stage,
                message,
                cause);
    }

    private record VectorizeRequest(List<Document> documents) {
        private record Document(
                Integer bidRecordId,
                String content,
                Integer invitationId,
                String bidderName,
                Integer bidNo,
                Long bidderAccountId,
                String projectNo,
                String purchaseTaskNo,
                String bidAt,
                String quotePrice) {
        }
    }

    private record VectorizeResponse(List<Document> documents) {
        private record Document(
                Integer bidRecordId,
                boolean success,
                Integer chunkCount,
                String message) {
        }
    }

    private record StatusResponse(Integer bidRecordId, int chunkCount) {
    }

    private record DeleteResponse(
            Integer bidRecordId,
            Integer deletedChunkCount) {
    }
}
