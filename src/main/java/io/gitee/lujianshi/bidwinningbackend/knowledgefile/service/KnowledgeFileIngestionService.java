package io.gitee.lujianshi.bidwinningbackend.knowledgefile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.ToMarkdownClient;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.KnowledgeFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.client.ChatAgentIngestionClient;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.dto.KnowledgeFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.DocumentUuidGenerator;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.KnowledgeObjectKeyGenerator;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.PreparedKnowledgeDocument;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.StoredKnowledgeObject;
import io.gitee.lujianshi.bidwinningbackend.mapper.KnowledgeFileMapper;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.annotation.Lazy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识文件入库编排 Service：original → Markdown → Chroma，全程落库追溯。
 *
 * <p>每个文件独立成一条 {@code knowledge_file} 记录，状态按
 * 转换（UPLOADED→CONVERTING→CONVERTED/FAILED）与向量化
 * （PENDING→VECTORIZING→VECTORIZED/FAILED）两个维度推进。</p>
 *
 * <p>与资信文件不同，本链路不在失败时删除对象/记录：失败只写状态与错误原因，
 * 对象保留用于按失败点重试；显式删除接口才清理向量、对象并软删除记录。</p>
 */
@Service
public class KnowledgeFileIngestionService {

    private static final Logger log = LoggerFactory.getLogger(
            KnowledgeFileIngestionService.class);
    private static final String MARKDOWN_CONTENT_TYPE =
            "text/markdown; charset=UTF-8";

    /** 转换状态。 */
    private static final String ST_CONVERTING = "CONVERTING";
    private static final String ST_CONVERTED = "CONVERTED";
    private static final String ST_CONVERT_FAILED = "FAILED";
    /** 向量化状态。 */
    private static final String ST_VECTORIZING = "VECTORIZING";
    private static final String ST_VECTORIZED = "VECTORIZED";
    private static final String ST_VECTORIZE_FAILED = "FAILED";
    /** 失败阶段。 */
    private static final String STAGE_UPLOAD = "UPLOAD";
    private static final String STAGE_CONVERT = "CONVERT";
    private static final String STAGE_VECTORIZE = "VECTORIZE";
    private static final String STAGE_UNKNOWN = "UNKNOWN";
    /** 删除终态标记（与 V44 deletion_status 对齐）。 */
    private static final String DELETION_DELETED = "DELETED";
    /** DELETING 超过该时长视为进程崩溃残留，允许其他请求抢占续删。 */
    private static final long DELETE_STALE_SECONDS = 10 * 60;

    private final DocumentUuidGenerator uuidGenerator;
    private final KnowledgeObjectKeyGenerator keyGenerator;
    private final KnowledgeFileObjectService objectService;
    private final ToMarkdownClient toMarkdownClient;
    private final ChatAgentIngestionClient chatAgentClient;
    private final KnowledgeFileMapper knowledgeFileMapper;
    private final KnowledgeFileStorageProperties storageProperties;

    @Resource
    private OperationHistoryService operationHistoryService;

    @Resource
    private FileStatusEventPublisher fileStatusEventPublisher;

    /** 通过 Spring 代理触发 @Async，避免同类调用绕过异步拦截器。 */
    @Resource
    @Lazy
    private KnowledgeFileIngestionService self;

    public KnowledgeFileIngestionService(
            DocumentUuidGenerator uuidGenerator,
            KnowledgeObjectKeyGenerator keyGenerator,
            KnowledgeFileObjectService objectService,
            ToMarkdownClient toMarkdownClient,
            ChatAgentIngestionClient chatAgentClient,
            KnowledgeFileMapper knowledgeFileMapper,
            KnowledgeFileStorageProperties storageProperties) {
        this.uuidGenerator = uuidGenerator;
        this.keyGenerator = keyGenerator;
        this.objectService = objectService;
        this.toMarkdownClient = toMarkdownClient;
        this.chatAgentClient = chatAgentClient;
        this.knowledgeFileMapper = knowledgeFileMapper;
        this.storageProperties = storageProperties;
    }

    /**
     * 上传一个或多个知识文件：每个文件独立完成入库并独立落库。
     *
     * <p>单个文件失败只在该文件记录上留痕，不抛异常、不连坐同批其他文件；
     * 调用方按响应中的 {@code documentUuid} 继续重试。</p>
     */
    public KnowledgeFileUploadResponse ingest(
            List<KnowledgeFileUpload> uploads,
            String username) {
        validateUploads(uploads);
        validateUsername(username);
        List<KnowledgeFileUploadResponse.Document> documents = new ArrayList<>();
        for (KnowledgeFileUpload upload : uploads) {
            documents.add(ingestOne(upload, username));
        }
        return new KnowledgeFileUploadResponse(documents);
    }

    /** 分页查询未删除的知识文件记录（PageHelper 负责分页与排序）。 */
    public List<KnowledgeFileRecord> list(
            String keyword,
            String convertStatus,
            String indexStatus) {
        return knowledgeFileMapper.selectPage(
                normalizeKeyword(keyword),
                normalizeKeyword(convertStatus),
                normalizeKeyword(indexStatus));
    }

    /** 查询单个知识文件记录，不存在或已删除时抛 404。 */
    public KnowledgeFileRecord getByDocumentUuid(String documentUuid) {
        return loadActive(documentUuid);
    }

    /**
     * 按失败点重试知识文件：未 CONVERTED 则重转，未 VECTORIZED 则重向量化。
     *
     * <p>向量化重试前先向 chat-agent 查询当前 chunk 数：向量已存在则直接落库
     * VECTORIZED，否则重新向量化（chat-agent 对同一 UUID 采用替换语义，不会重复）。</p>
     *
     * @return 重试后的最新记录（无论成功或失败，状态都会更新）
     */
    public KnowledgeFileRecord retry(String documentUuid) {
        KnowledgeFileRecord record = loadActive(documentUuid);
        try {
            if (!ST_CONVERTED.equals(record.getConvertStatus())) {
                markConverting(documentUuid);
                KnowledgeFileRecord beforeConvert =
                        knowledgeFileMapper.selectByDocumentUuid(documentUuid);
                PreparedKnowledgeDocument prepared = convertFromRecord(record);
                markConverted(documentUuid, prepared);
                operationHistoryService.recordKnowledgeFile(
                        documentUuid,
                        OperationType.UPDATE,
                        beforeConvert,
                        knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                        null);
                record = knowledgeFileMapper.selectByDocumentUuid(documentUuid);
            }

            if (record != null && !ST_VECTORIZED.equals(record.getIndexStatus())) {
                Integer existingChunkCount =
                        chatAgentClient.getVectorStatus(documentUuid);
                if (existingChunkCount != null && existingChunkCount > 0) {
                    markVectorizing(documentUuid);
                    markVectorized(documentUuid, existingChunkCount);
                    operationHistoryService.recordKnowledgeFile(
                            documentUuid,
                            OperationType.UPDATE,
                            record,
                            knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                            "向量已存在，直接对账落库");
                } else {
                    PreparedKnowledgeDocument prepared =
                            preparedFromRecord(record);
                    markVectorizing(documentUuid);
                    int chunkCount = chatAgentClient.vectorizeDocument(prepared);
                    markVectorized(documentUuid, chunkCount);
                    operationHistoryService.recordKnowledgeFile(
                            documentUuid,
                            OperationType.UPDATE,
                            record,
                            knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                            null);
                }
            }
            return knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        } catch (AppException failure) {
            markRetryFailure(documentUuid, failure);
            return knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        } catch (RuntimeException failure) {
            markRetryFailure(documentUuid, failure);
            return knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        }
    }

    /**
     * 删除知识文件：可追溯、可幂等续删的状态机。
     *
     * <p>先原子抢占 DELETING，再依次删 Chroma 向量、对象存储 original/converted、
     * 软删除数据库记录并写删除历史；任一步失败都会把失败点与原因落库后抛错，
     * 记录保持 active，再次调用本方法会从头幂等重放并收敛。历史写入失败导致
     * 已软删但未终态时，重试走补记历史分支。</p>
     */
    public void delete(String documentUuid) {
        KnowledgeFileRecord record =
                knowledgeFileMapper.selectByDocumentUuidIncludingDeleted(
                        documentUuid);
        if (record == null) {
            throw notFound(documentUuid);
        }
        if (record.getDeletedAt() != null) {
            if (DELETION_DELETED.equals(record.getDeletionStatus())) {
                return;
            }
            completeDeletionHistory(documentUuid, record);
            return;
        }

        LocalDateTime startedAt = LocalDateTime.now();
        int claimed = knowledgeFileMapper.beginDeletion(
                documentUuid,
                startedAt,
                startedAt.minusSeconds(DELETE_STALE_SECONDS));
        if (claimed != 1) {
            KnowledgeFileRecord latest =
                    knowledgeFileMapper.selectByDocumentUuidIncludingDeleted(
                            documentUuid);
            if (latest != null && latest.getDeletedAt() != null) {
                if (DELETION_DELETED.equals(latest.getDeletionStatus())) {
                    return;
                }
                completeDeletionHistory(documentUuid, latest);
                return;
            }
            throw deleteInProgress(documentUuid);
        }

        try {
            try {
                chatAgentClient.deleteDocument(documentUuid);
            } catch (AppException failure) {
                throw deleteFailure(
                        "DELETE_VECTORS", "知识文件向量删除失败", failure);
            }
            deleteObject(
                    record.getOriginalFilePath(),
                    documentUuid,
                    "DELETE_OBJECT_ORIGINAL");
            deleteObject(
                    record.getMarkdownFilePath(),
                    documentUuid,
                    "DELETE_OBJECT_CONVERTED");
            int rows = knowledgeFileMapper.markDeleted(documentUuid, startedAt);
            if (rows != 1) {
                throw deleteFailure(
                        "MARK_DELETED", "知识文件记录软删除失败", null);
            }
            try {
                operationHistoryService.recordKnowledgeFile(
                        documentUuid,
                        OperationType.DELETE,
                        record,
                        null,
                        null);
            } catch (RuntimeException historyFailure) {
                markDeletionFailed(
                        documentUuid,
                        "HISTORY",
                        toErrorMessage(historyFailure));
                throw deleteFailure(
                        "HISTORY", "知识文件删除历史写入失败", historyFailure);
            }
            knowledgeFileMapper.markDeletionSucceeded(documentUuid);
        } catch (AppException failure) {
            markDeletionFailed(
                    documentUuid,
                    failure.getStage(),
                    toErrorMessage(failure));
            throw failure;
        } catch (RuntimeException failure) {
            markDeletionFailed(
                    documentUuid,
                    STAGE_UNKNOWN,
                    toErrorMessage(failure));
            throw failure;
        }
    }

    // ==================== 单文件入库 ====================

    private KnowledgeFileUploadResponse.Document ingestOne(
            KnowledgeFileUpload upload,
            String username) {
        String documentUuid = uuidGenerator.generate().toString();
        insertRecord(documentUuid, upload);

        String stage = STAGE_UPLOAD;
        try {
            OriginalFile original = storeOriginal(documentUuid, upload, username);
            updateOriginalMetadata(documentUuid, original);
            operationHistoryService.recordKnowledgeFile(
                    documentUuid,
                    OperationType.CREATE,
                    null,
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                    null);
            publishStatus(documentUuid);
            // 单元测试直接构造 Service 时没有 Spring 代理，回退为同步执行；
            // 正式运行由 self 代理触发 @Async。
            if (self == null) {
                processStoredFileAsync(documentUuid);
            } else {
                self.processStoredFileAsync(documentUuid);
            }
            return toQueuedResponse(
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid));
        } catch (AppException failure) {
            return markIngestFailure(documentUuid, failure, stage);
        } catch (RuntimeException failure) {
            return markIngestFailure(documentUuid, failure, stage);
        }
    }

    /** 原始对象成功落库后，在后台完成转换和向量化，不阻塞上传请求。 */
    @Async("fileProcessingExecutor")
    public void processStoredFileAsync(String documentUuid) {
        String stage = STAGE_CONVERT;
        try {
            KnowledgeFileRecord record = loadActive(documentUuid);
            markConverting(documentUuid);
            KnowledgeFileRecord beforeConvert =
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid);
            PreparedKnowledgeDocument prepared = convertFromRecord(record);
            markConverted(documentUuid, prepared);
            operationHistoryService.recordKnowledgeFile(
                    documentUuid,
                    OperationType.UPDATE,
                    beforeConvert,
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                    null);

            stage = STAGE_VECTORIZE;
            markVectorizing(documentUuid);
            KnowledgeFileRecord beforeIndex =
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid);
            int chunkCount = chatAgentClient.vectorizeDocument(prepared);
            markVectorized(documentUuid, chunkCount);
            operationHistoryService.recordKnowledgeFile(
                    documentUuid,
                    OperationType.UPDATE,
                    beforeIndex,
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                    null);
        } catch (AppException failure) {
            markIngestFailure(documentUuid, failure, stage);
        } catch (RuntimeException failure) {
            markIngestFailure(documentUuid, failure, stage);
        }
    }

    private OriginalFile storeOriginal(
            String documentUuid,
            KnowledgeFileUpload upload,
            String username) {
        KnowledgeObjectKeyGenerator.OriginalObjectKey key;
        try {
            key = keyGenerator.original(
                    username,
                    UUID.fromString(documentUuid),
                    upload.originalFilename());
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
                    upload.contentType(),
                    upload.fileSize(),
                    Map.of(
                            "document-uuid", documentUuid,
                            "file-kind", "original"),
                    upload.content());
            return new OriginalFile(
                    documentUuid,
                    upload,
                    key.safeOriginalFilename(),
                    stored);
        } catch (IOException failure) {
            throw storageFailure("STORE_ORIGINAL", "原始知识文件存储失败", failure);
        }
    }

    private PreparedKnowledgeDocument convertAndStore(
            String documentUuid,
            KnowledgeFileUpload upload,
            OriginalFile originalFile,
            String username) {
        PythonMarkdownResponse response;
        try (InputStream input = objectService.open(
                originalFile.storedObject())) {
            response = toMarkdownClient.convert(
                    input,
                    originalFile.safeOriginalFilename(),
                    originalFile.upload().contentType(),
                    originalFile.storedObject().fileSize());
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
        byte[] markdown = response.markdown();

        String convertedKey = keyGenerator.converted(
                username,
                originalFile.safeOriginalFilename(),
                response.filename());
        try {
            StoredKnowledgeObject converted = objectService.storeAndVerify(
                    convertedKey,
                    MARKDOWN_CONTENT_TYPE,
                    markdown.length,
                    Map.of(
                            "document-uuid", documentUuid,
                            "file-kind", "converted"),
                    () -> new ByteArrayInputStream(markdown));
            return new PreparedKnowledgeDocument(
                    documentUuid,
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

    /** 重试时从数据库记录重建 converted 文档（复用已存储的 original）。 */
    private PreparedKnowledgeDocument convertFromRecord(
            KnowledgeFileRecord record) {
        String username = usernameFromPath(record.getOriginalFilePath());
        StoredKnowledgeObject original = new StoredKnowledgeObject(
                record.getOriginalFilePath(),
                record.getFileSize() == null ? 0 : record.getFileSize(),
                record.getContentType(),
                record.getOriginalSha256(),
                null);
        PythonMarkdownResponse response;
        try (InputStream input = objectService.open(original)) {
            response = toMarkdownClient.convert(
                    input,
                    record.getOriginalFilename(),
                    record.getContentType(),
                    record.getFileSize() == null ? 0 : record.getFileSize());
        } catch (IOException failure) {
            throw storageFailure("READ_ORIGINAL", "知识文件原始内容读取失败", failure);
        } catch (AppException failure) {
            throw new AppException(
                    AppErrorCode.KNOWLEDGE_FILE_CONVERSION_FAILED,
                    "knowledge-file",
                    "RETRY",
                    "CONVERT_ORIGINAL",
                    "知识文件转换失败",
                    failure);
        }
        byte[] markdown = response.markdown();

        String convertedKey = keyGenerator.converted(
                username,
                safeFilenameFromStored(storedFilename(record.getOriginalFilePath())),
                response.filename());
        try {
            StoredKnowledgeObject converted = objectService.storeAndVerify(
                    convertedKey,
                    MARKDOWN_CONTENT_TYPE,
                    markdown.length,
                    Map.of(
                            "document-uuid", record.getDocumentUuid(),
                            "file-kind", "converted"),
                    () -> new ByteArrayInputStream(markdown));
            return new PreparedKnowledgeDocument(
                    record.getDocumentUuid(),
                    record.getOriginalFilename(),
                    record.getOriginalFilePath(),
                    converted.objectKey(),
                    record.getOriginalSha256(),
                    converted.sha256(),
                    markdown);
        } catch (IOException failure) {
            throw storageFailure("STORE_CONVERTED", "Markdown 知识文件存储失败", failure);
        }
    }

    /** 从数据库记录读取已转换 Markdown，用于只重跑向量化。 */
    private PreparedKnowledgeDocument preparedFromRecord(
            KnowledgeFileRecord record) {
        byte[] markdown;
        try (InputStream input = objectService.open(new StoredKnowledgeObject(
                record.getMarkdownFilePath(),
                -1,
                MARKDOWN_CONTENT_TYPE,
                record.getConvertedSha256(),
                null))) {
            markdown = input.readAllBytes();
        } catch (IOException failure) {
            throw storageFailure("READ_CONVERTED", "知识文件 Markdown 读取失败", failure);
        }
        return new PreparedKnowledgeDocument(
                record.getDocumentUuid(),
                record.getOriginalFilename(),
                record.getOriginalFilePath(),
                record.getMarkdownFilePath(),
                record.getOriginalSha256(),
                record.getConvertedSha256(),
                markdown);
    }

    // ==================== 数据库状态推进 ====================

    private void insertRecord(String documentUuid, KnowledgeFileUpload upload) {
        KnowledgeFileRecord record = new KnowledgeFileRecord();
        record.setDocumentUuid(documentUuid);
        record.setOriginalFilename(upload.originalFilename());
        record.setContentType(upload.contentType());
        record.setFileSize(upload.fileSize());
        record.setStorageType(storageProperties.getType().name());
        record.setOriginalFilePath("");
        record.setOriginalStorageStatus("STORING");
        record.setConvertStatus("UPLOADED");
        record.setIndexStatus("PENDING");
        record.setCreatedAt(LocalDateTime.now());
        int rows = knowledgeFileMapper.insert(record);
        if (rows != 1) {
            throw persistenceFailure("INSERT", "知识文件记录创建失败");
        }
    }

    private void updateOriginalMetadata(
            String documentUuid,
            OriginalFile originalFile) {
        int rows = knowledgeFileMapper.updateOriginalMetadata(
                documentUuid,
                originalFile.storedObject().objectKey(),
                originalFile.storedObject().sha256(),
                storageProperties.getType().name());
        requireRow(rows, "UPDATE_ORIGINAL", "知识文件原始对象元数据回填失败");
    }

    private void markConverting(String documentUuid) {
        requireRow(
                knowledgeFileMapper.markConverting(documentUuid),
                "MARK_CONVERTING",
                "知识文件转换状态抢占失败");
        publishStatus(documentUuid);
    }

    private void markConverted(
            String documentUuid,
            PreparedKnowledgeDocument document) {
        requireRow(
                knowledgeFileMapper.markConverted(
                        documentUuid,
                        document.convertedObjectKey(),
                        document.convertedSha256(),
                        LocalDateTime.now()),
                "MARK_CONVERTED",
                "知识文件转换完成状态更新失败");
        publishStatus(documentUuid);
    }

    private void markVectorizing(String documentUuid) {
        requireRow(
                knowledgeFileMapper.markVectorizing(documentUuid),
                "MARK_VECTORIZING",
                "知识文件向量化状态抢占失败");
        publishStatus(documentUuid);
    }

    private void markVectorized(String documentUuid, int chunkCount) {
        requireRow(
                knowledgeFileMapper.markVectorized(
                        documentUuid, chunkCount, LocalDateTime.now()),
                "MARK_VECTORIZED",
                "知识文件向量化完成状态更新失败");
        publishStatus(documentUuid);
    }

    private void requireRow(int rows, String stage, String message) {
        if (rows != 1) {
            throw persistenceFailure(stage, message);
        }
    }

    private KnowledgeFileUploadResponse.Document markIngestFailure(
            String documentUuid,
            Throwable failure,
            String stage) {
        String message = toErrorMessage(failure);
        try {
            if (STAGE_UPLOAD.equals(stage)) {
                knowledgeFileMapper.markOriginalStorageFailed(documentUuid, message);
            } else if (STAGE_VECTORIZE.equals(stage)) {
                knowledgeFileMapper.markVectorizeFailed(documentUuid, message);
            } else {
                knowledgeFileMapper.markConvertFailed(documentUuid, message);
            }
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "知识文件失败状态落库失败: documentUuid={}",
                    documentUuid,
                    persistenceFailure);
        }
        KnowledgeFileRecord record =
                knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        operationHistoryService.recordKnowledgeFile(
                documentUuid,
                OperationType.UPDATE,
                null,
                record,
                message);
        publishStatus(documentUuid);
        return toFailureResponse(record, documentUuid, message);
    }

    private void markRetryFailure(
            String documentUuid,
            Throwable failure) {
        KnowledgeFileRecord record =
                knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        String stage = record == null ? STAGE_UNKNOWN
                : ST_CONVERTING.equals(record.getConvertStatus())
                ? STAGE_CONVERT
                : ST_VECTORIZING.equals(record.getIndexStatus())
                ? STAGE_VECTORIZE
                : STAGE_UNKNOWN;
        String message = toErrorMessage(failure);
        try {
            if (STAGE_VECTORIZE.equals(stage)) {
                knowledgeFileMapper.markVectorizeFailed(documentUuid, message);
            } else {
                knowledgeFileMapper.markConvertFailed(documentUuid, message);
            }
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "知识文件重试失败状态落库失败: documentUuid={}",
                    documentUuid,
                    persistenceFailure);
        }
        operationHistoryService.recordKnowledgeFile(
                documentUuid,
                OperationType.UPDATE,
                null,
                knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                message);
        publishStatus(documentUuid);
    }

    private void publishStatus(String documentUuid) {
        if (fileStatusEventPublisher == null) return;
        KnowledgeFileRecord latest = knowledgeFileMapper.selectByDocumentUuid(
                documentUuid);
        if (latest == null) return;
        fileStatusEventPublisher.publish(
                FileStatusEventPublisher.KNOWLEDGE_TOPIC,
                latest.getDocumentUuid(),
                latest.getConvertStatus(),
                latest.getIndexStatus(),
                latest.getOriginalStorageStatus());
    }

    private KnowledgeFileUploadResponse.Document toQueuedResponse(
            KnowledgeFileRecord record) {
        return new KnowledgeFileUploadResponse.Document(
                record.getDocumentUuid(),
                record.getOriginalFilename(),
                record.getOriginalFilePath(),
                record.getMarkdownFilePath(),
                record.getOriginalSha256(),
                record.getConvertedSha256(),
                record.getChunkCount(),
                record.getOriginalStorageStatus(),
                record.getConvertStatus(),
                record.getIndexStatus(),
                record.getErrorMessage());
    }

    private KnowledgeFileUploadResponse.Document toSuccessResponse(
            PreparedKnowledgeDocument document,
            int chunkCount) {
        return new KnowledgeFileUploadResponse.Document(
                document.documentUuid(),
                document.originalFilename(),
                document.originalObjectKey(),
                document.convertedObjectKey(),
                document.originalSha256(),
                document.convertedSha256(),
                chunkCount,
                "STORED",
                ST_CONVERTED,
                ST_VECTORIZED,
                null);
    }

    private KnowledgeFileUploadResponse.Document toFailureResponse(
            KnowledgeFileRecord record,
            String documentUuid,
            String message) {
        return new KnowledgeFileUploadResponse.Document(
                documentUuid,
                record == null ? "" : record.getOriginalFilename(),
                record == null ? null : record.getOriginalFilePath(),
                record == null ? null : record.getMarkdownFilePath(),
                record == null ? null : record.getOriginalSha256(),
                record == null ? null : record.getConvertedSha256(),
                record == null ? null : record.getChunkCount(),
                record == null ? "FAILED" : record.getOriginalStorageStatus(),
                record == null ? ST_CONVERT_FAILED : record.getConvertStatus(),
                record == null ? ST_VECTORIZE_FAILED : record.getIndexStatus(),
                message);
    }

    private KnowledgeFileRecord loadActive(String documentUuid) {
        if (documentUuid == null || documentUuid.isBlank()) {
            throw notFound(documentUuid);
        }
        KnowledgeFileRecord record =
                knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        if (record == null || record.getDeletedAt() != null) {
            throw notFound(documentUuid);
        }
        return record;
    }

    private void deleteObject(
            String objectKey,
            String documentUuid,
            String stage) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            objectService.delete(objectKey);
        } catch (IOException | RuntimeException failure) {
            throw deleteFailure(
                    stage,
                    "知识文件对象删除失败: " + documentUuid,
                    failure);
        }
    }

    /** 已软删但未完成终态（历史未写入）时补记删除历史并置 DELETED。 */
    private void completeDeletionHistory(
            String documentUuid,
            KnowledgeFileRecord record) {
        try {
            operationHistoryService.recordKnowledgeFile(
                    documentUuid,
                    OperationType.DELETE,
                    record,
                    null,
                    "删除历史补记（删除流程中断恢复）");
            knowledgeFileMapper.markDeletionSucceeded(documentUuid);
        } catch (RuntimeException historyFailure) {
            markDeletionFailed(
                    documentUuid,
                    "HISTORY",
                    toErrorMessage(historyFailure));
            throw deleteFailure(
                    "HISTORY", "知识文件删除历史补记失败", historyFailure);
        }
    }

    /** 删除失败落库：保留失败点与原因，供追溯与重试。 */
    private void markDeletionFailed(
            String documentUuid,
            String stage,
            String errorMessage) {
        knowledgeFileMapper.markDeletionFailed(
                documentUuid, stage, errorMessage);
    }

    private AppException deleteInProgress(String documentUuid) {
        return new AppException(
                AppErrorCode.KNOWLEDGE_FILE_DELETE_IN_PROGRESS,
                "knowledge-file",
                "DELETE",
                "IN_PROGRESS",
                "知识文件正在删除中，请稍后重试");
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

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new AppException(
                    AppErrorCode.KNOWLEDGE_FILE_INVALID_UPLOAD_REQUEST,
                    "knowledge-file",
                    "INGEST",
                    "VALIDATE_REQUEST",
                    "上传知识文件必须携带用户名");
        }
    }

    /** 从已存储的 original 路径解析用户名（路径第一段）。 */
    private String usernameFromPath(String originalFilePath) {
        if (originalFilePath == null || originalFilePath.isBlank()) {
            throw new IllegalArgumentException("知识文件原始路径不能为空");
        }
        int slash = originalFilePath.indexOf('/');
        if (slash <= 0) {
            throw new IllegalArgumentException("知识文件原始路径缺少用户名目录");
        }
        return originalFilePath.substring(0, slash);
    }

    /** 从已存储的 original 路径解析 original 文件名（original 目录后的部分）。 */
    private String storedFilename(String originalFilePath) {
        String marker = "/original/";
        int markerIndex = originalFilePath.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalArgumentException("知识文件原始路径缺少 original 目录");
        }
        return originalFilePath.substring(markerIndex + marker.length());
    }

    /** 从 {@code {uuid}_{safeFilename}} 存储文件名还原安全原始文件名。 */
    private String safeFilenameFromStored(String storedFilename) {
        int underscore = storedFilename.indexOf('_');
        if (underscore <= 0 || underscore == storedFilename.length() - 1) {
            throw new IllegalArgumentException("知识文件存储文件名缺少 UUID 前缀");
        }
        return storedFilename.substring(underscore + 1);
    }

    private String normalizeKeyword(String value) {
        return value == null ? null : value.trim();
    }

    private String toErrorMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure == null
                    ? "未知错误"
                    : failure.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private AppException notFound(String documentUuid) {
        return new AppException(
                AppErrorCode.KNOWLEDGE_FILE_NOT_FOUND,
                "knowledge-file",
                "KNOWLEDGE_FILE",
                "LOAD_RECORD",
                "知识文件不存在: " + documentUuid);
    }

    private AppException persistenceFailure(String stage, String message) {
        return new AppException(
                AppErrorCode.KNOWLEDGE_FILE_PERSISTENCE_FAILED,
                "knowledge-file",
                "PERSISTENCE",
                stage,
                message);
    }

    private AppException storageFailure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.KNOWLEDGE_FILE_STORAGE_FAILED,
                "knowledge-file",
                "STORAGE",
                stage,
                message,
                cause);
    }

    private AppException deleteFailure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.KNOWLEDGE_FILE_DELETE_FAILED,
                "knowledge-file",
                "DELETE",
                stage,
                message,
                cause);
    }

    private record OriginalFile(
            String documentUuid,
            KnowledgeFileUpload upload,
            String safeOriginalFilename,
            StoredKnowledgeObject storedObject) {
    }
}
