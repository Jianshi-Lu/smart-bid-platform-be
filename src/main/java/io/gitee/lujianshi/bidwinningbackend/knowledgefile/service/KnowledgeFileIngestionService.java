package io.gitee.lujianshi.bidwinningbackend.knowledgefile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.ToMarkdownClient;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.ChatAgentIngestionClient;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.dto.ChatAgentVectorizeResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.dto.KnowledgeFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.DocumentUuidGenerator;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.KnowledgeObjectKeyGenerator;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.PreparedKnowledgeDocument;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.StoredKnowledgeObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 同步编排 original、converted 和 Chroma 的完整 knowledge-file 链路。 */
@Service
public class KnowledgeFileIngestionService {

    private static final Logger log = LoggerFactory.getLogger(
            KnowledgeFileIngestionService.class);
    private static final String MARKDOWN_CONTENT_TYPE =
            "text/markdown; charset=UTF-8";

    private final DocumentUuidGenerator uuidGenerator;
    private final KnowledgeObjectKeyGenerator keyGenerator;
    private final KnowledgeFileObjectService objectService;
    private final ToMarkdownClient toMarkdownClient;
    private final ChatAgentIngestionClient chatAgentClient;

    public KnowledgeFileIngestionService(
            DocumentUuidGenerator uuidGenerator,
            KnowledgeObjectKeyGenerator keyGenerator,
            KnowledgeFileObjectService objectService,
            ToMarkdownClient toMarkdownClient,
            ChatAgentIngestionClient chatAgentClient) {
        this.uuidGenerator = uuidGenerator;
        this.keyGenerator = keyGenerator;
        this.objectService = objectService;
        this.toMarkdownClient = toMarkdownClient;
        this.chatAgentClient = chatAgentClient;
    }

    /** 只有全部文件可被 chat-agent 检索时才返回成功。 */
    public KnowledgeFileUploadResponse ingest(List<KnowledgeFileUpload> uploads) {
        validateUploads(uploads);

        List<PendingFile> pendingFiles = uploads.stream()
                .map(upload -> new PendingFile(uuidGenerator.generate(), upload))
                .toList();
        List<OriginalFile> originalFiles = new ArrayList<>();
        List<PreparedKnowledgeDocument> preparedDocuments = new ArrayList<>();
        boolean vectorizationAttempted = false;

        try {
            // 多文件请求先完成全部 original，避免转换阶段读取半套输入。
            for (PendingFile pendingFile : pendingFiles) {
                originalFiles.add(storeOriginal(pendingFile));
            }
            for (OriginalFile originalFile : originalFiles) {
                preparedDocuments.add(convertAndStore(originalFile));
            }

            vectorizationAttempted = true;
            List<ChatAgentVectorizeResponse.Document> vectorized =
                    chatAgentClient.vectorizeDocuments(preparedDocuments);
            Map<String, Integer> chunkCounts = new LinkedHashMap<>();
            for (ChatAgentVectorizeResponse.Document document : vectorized) {
                chunkCounts.put(document.documentUuid(), document.chunkCount());
            }

            return new KnowledgeFileUploadResponse(
                    preparedDocuments.stream()
                            .map(document -> toResponse(
                                    document,
                                    chunkCounts.get(document.documentUuid())))
                            .toList());
        } catch (AppException failure) {
            compensate(
                    pendingFiles,
                    originalFiles,
                    preparedDocuments,
                    vectorizationAttempted,
                    failure);
            throw failure;
        } catch (RuntimeException failure) {
            compensate(
                    pendingFiles,
                    originalFiles,
                    preparedDocuments,
                    vectorizationAttempted,
                    failure);
            throw new AppException(
                    AppErrorCode.KNOWLEDGE_FILE_STORAGE_FAILED,
                    "knowledge-file",
                    "INGEST",
                    "UNEXPECTED_FAILURE",
                    "知识文件处理失败",
                    failure);
        }
    }

    private OriginalFile storeOriginal(PendingFile pendingFile) {
        KnowledgeObjectKeyGenerator.OriginalObjectKey key;
        try {
            key = keyGenerator.original(
                    pendingFile.documentUuid(),
                    pendingFile.upload().originalFilename());
        } catch (IllegalArgumentException failure) {
            throw new AppException(
                    AppErrorCode.KNOWLEDGE_FILE_INVALID_FILE,
                    "knowledge-file",
                    "INGEST",
                    "BUILD_ORIGINAL_KEY",
                    "知识文件名不合法",
                    failure);
        }

        try {
            StoredKnowledgeObject stored = objectService.storeAndVerify(
                    key.objectKey(),
                    pendingFile.upload().contentType(),
                    pendingFile.upload().fileSize(),
                    Map.of(
                            "document-uuid", pendingFile.documentUuid().toString(),
                            "file-kind", "original"),
                    pendingFile.upload().content());
            return new OriginalFile(
                    pendingFile.documentUuid(),
                    pendingFile.upload(),
                    key.safeOriginalFilename(),
                    stored);
        } catch (IOException failure) {
            throw storageFailure("STORE_ORIGINAL", "原始知识文件存储失败", failure);
        }
    }

    private PreparedKnowledgeDocument convertAndStore(OriginalFile originalFile) {
        byte[] markdown;
        try (InputStream input = objectService.open(originalFile.storedObject())) {
            PythonMarkdownResponse response = toMarkdownClient.convert(
                    input,
                    originalFile.safeOriginalFilename(),
                    originalFile.upload().contentType(),
                    originalFile.storedObject().fileSize());
            markdown = response.markdown();
        } catch (IOException failure) {
            throw storageFailure("READ_ORIGINAL", "原始知识文件读取失败", failure);
        } catch (AppException failure) {
            throw new AppException(
                    AppErrorCode.KNOWLEDGE_FILE_CONVERSION_FAILED,
                    "knowledge-file",
                    "INGEST",
                    "CONVERT_ORIGINAL",
                    "知识文件转换失败",
                    failure);
        }

        String convertedKey = keyGenerator.converted(originalFile.documentUuid());
        try {
            StoredKnowledgeObject converted = objectService.storeAndVerify(
                    convertedKey,
                    MARKDOWN_CONTENT_TYPE,
                    markdown.length,
                    Map.of(
                            "document-uuid", originalFile.documentUuid().toString(),
                            "file-kind", "converted"),
                    () -> new ByteArrayInputStream(markdown));
            return new PreparedKnowledgeDocument(
                    originalFile.documentUuid().toString(),
                    originalFile.safeOriginalFilename(),
                    originalFile.storedObject().objectKey(),
                    converted.objectKey(),
                    originalFile.storedObject().sha256(),
                    converted.sha256(),
                    markdown);
        } catch (IOException failure) {
            throw storageFailure("STORE_CONVERTED", "Markdown 知识文件存储失败", failure);
        }
    }

    private KnowledgeFileUploadResponse.Document toResponse(
            PreparedKnowledgeDocument document,
            Integer chunkCount) {
        if (chunkCount == null || chunkCount <= 0) {
            throw new AppException(
                    AppErrorCode.KNOWLEDGE_FILE_VECTORIZATION_FAILED,
                    "knowledge-file",
                    "INGEST",
                    "BUILD_RESPONSE",
                    "知识文件向量化结果缺失");
        }
        return new KnowledgeFileUploadResponse.Document(
                document.documentUuid(),
                document.originalFilename(),
                document.originalObjectKey(),
                document.convertedObjectKey(),
                document.originalSha256(),
                document.convertedSha256(),
                chunkCount);
    }

    private void validateUploads(List<KnowledgeFileUpload> uploads) {
        if (uploads == null || uploads.isEmpty()
                || uploads.stream().anyMatch(upload -> upload == null)) {
            throw new AppException(
                    AppErrorCode.KNOWLEDGE_FILE_INVALID_UPLOAD_REQUEST,
                    "knowledge-file",
                    "INGEST",
                    "VALIDATE_REQUEST",
                    "上传知识文件不能为空");
        }
    }

    private AppException storageFailure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.KNOWLEDGE_FILE_STORAGE_FAILED,
                "knowledge-file",
                "INGEST",
                stage,
                message,
                cause);
    }

    private void compensate(
            List<PendingFile> pendingFiles,
            List<OriginalFile> originalFiles,
            List<PreparedKnowledgeDocument> preparedDocuments,
            boolean vectorizationAttempted,
            Throwable originalFailure) {
        if (vectorizationAttempted) {
            for (PendingFile pendingFile : pendingFiles) {
                try {
                    chatAgentClient.deleteDocument(
                            pendingFile.documentUuid().toString());
                } catch (RuntimeException cleanupFailure) {
                    recordCleanupFailure(
                            "Chroma",
                            pendingFile.documentUuid().toString(),
                            originalFailure,
                            cleanupFailure);
                }
            }
        }

        for (int index = preparedDocuments.size() - 1; index >= 0; index--) {
            PreparedKnowledgeDocument document = preparedDocuments.get(index);
            deleteObject(document.convertedObjectKey(), originalFailure);
        }
        for (int index = originalFiles.size() - 1; index >= 0; index--) {
            OriginalFile originalFile = originalFiles.get(index);
            deleteObject(originalFile.storedObject().objectKey(), originalFailure);
        }
    }

    private void deleteObject(String objectKey, Throwable originalFailure) {
        try {
            objectService.delete(objectKey);
        } catch (IOException | RuntimeException cleanupFailure) {
            recordCleanupFailure(
                    "object-storage",
                    objectKey,
                    originalFailure,
                    cleanupFailure);
        }
    }

    private void recordCleanupFailure(
            String target,
            String identity,
            Throwable originalFailure,
            Throwable cleanupFailure) {
        originalFailure.addSuppressed(cleanupFailure);
        log.warn("知识文件补偿失败: target={}, identity={}",
                target, identity, cleanupFailure);
    }

    private record PendingFile(
            UUID documentUuid,
            KnowledgeFileUpload upload) {
    }

    private record OriginalFile(
            UUID documentUuid,
            KnowledgeFileUpload upload,
            String safeOriginalFilename,
            StoredKnowledgeObject storedObject) {
    }
}
