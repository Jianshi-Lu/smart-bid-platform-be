package io.gitee.lujianshi.bidwinningbackend.knowledgefile.service;

import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.ToMarkdownClient;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.KnowledgeFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.dto.KnowledgeFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.DocumentUuidGenerator;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.KnowledgeObjectKeyGenerator;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.PreparedKnowledgeDocument;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.StoredKnowledgeObject;
import io.gitee.lujianshi.bidwinningbackend.mapper.KnowledgeFileMapper;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import io.gitee.lujianshi.bidwinningbackend.storage.vector.KnowledgeFileVectorStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.annotation.Lazy;

import java.io.ByteArrayInputStream;
import java.security.DigestInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
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
    private final KnowledgeFileVectorStorage vectorStorage;
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
            KnowledgeFileVectorStorage vectorStorage,
            KnowledgeFileMapper knowledgeFileMapper,
            KnowledgeFileStorageProperties storageProperties) {
        this.uuidGenerator = uuidGenerator;
        this.keyGenerator = keyGenerator;
        this.objectService = objectService;
        this.toMarkdownClient = toMarkdownClient;
        this.vectorStorage = vectorStorage;
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
     * <p>向量化重试始终从已校验的 Markdown 重新执行替换写入。多分片文档不能仅凭
     * “当前 chunk 数大于 0”判断历史未知结果是否完整。</p>
     *
     * @return 重试后的最新记录（无论成功或失败，状态都会更新）
     */
    public KnowledgeFileRecord retry(String documentUuid) {
        KnowledgeFileRecord record = loadActive(documentUuid);
        String stage = STAGE_CONVERT;
        try {
            if (!ST_CONVERTED.equals(record.getConvertStatus())) {
                markConverting(documentUuid);
                KnowledgeFileRecord beforeConvert =
                        knowledgeFileMapper.selectByDocumentUuid(documentUuid);
                PreparedKnowledgeDocument prepared = convertFromRecord(record);
                try {
                    markConverted(documentUuid, prepared);
                } catch (RuntimeException failure) {
                    cleanupUncommittedConvertedObject(
                            documentUuid, prepared, failure);
                    throw failure;
                }
                recordHistorySafely(
                        documentUuid,
                        OperationType.UPDATE,
                        beforeConvert,
                        knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                        null);
                record = knowledgeFileMapper.selectByDocumentUuid(documentUuid);
            }

            if (record != null && !ST_VECTORIZED.equals(record.getIndexStatus())) {
                stage = STAGE_VECTORIZE;
                PreparedKnowledgeDocument prepared =
                        preparedFromRecord(record);
                markVectorizing(documentUuid);
                int chunkCount = vectorStorage.vectorizeDocument(prepared);
                markVectorized(documentUuid, chunkCount);
                recordHistorySafely(
                        documentUuid,
                        OperationType.UPDATE,
                        record,
                        knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                        null);
            }
            return knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        } catch (AppException failure) {
            markRetryFailure(documentUuid, failure, stage);
            return knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        } catch (RuntimeException failure) {
            markRetryFailure(documentUuid, failure, stage);
            return knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        }
    }

    /**
     * JVM 重启后的知识文件对账入口：续删、清理未提交对象、恢复处理中断并校验 Chroma。
     */
    public void reconcileLifecycle(KnowledgeFileRecord snapshot) {
        if (snapshot == null || snapshot.getDocumentUuid() == null
                || snapshot.getDocumentUuid().isBlank()) {
            return;
        }
        String documentUuid = snapshot.getDocumentUuid();
        if (snapshot.getDeletedAt() != null
                && DELETION_DELETED.equals(snapshot.getDeletionStatus())) {
            return;
        }
        if (snapshot.getDeletedAt() != null
                || snapshot.getDeletionStatus() != null) {
            if ("DELETING".equals(snapshot.getDeletionStatus())) {
                knowledgeFileMapper.resetInterruptedDeletion(documentUuid);
            }
            delete(documentUuid);
            return;
        }

        if ("STORING".equals(snapshot.getOriginalStorageStatus())
                || "FAILED".equals(snapshot.getOriginalStorageStatus())) {
            RuntimeException cleanupFailure = null;
            try {
                vectorStorage.deleteDocument(documentUuid);
                deleteObjectIfUnreferenced(
                        snapshot.getMarkdownFilePath(),
                        documentUuid,
                        "RECOVER_DELETE_CONVERTED",
                        true);
                deleteObjectIfUnreferenced(
                        snapshot.getOriginalFilePath(),
                        documentUuid,
                        "RECOVER_DELETE_ORIGINAL",
                        false);
            } catch (RuntimeException failure) {
                cleanupFailure = failure;
            }
            if ("STORING".equals(snapshot.getOriginalStorageStatus())) {
                knowledgeFileMapper.markInterruptedProcessingFailed(
                        documentUuid, "后端进程重启，未提交上传已清理");
                publishStatus(documentUuid);
            }
            if (cleanupFailure != null) {
                throw cleanupFailure;
            }
            return;
        }

        boolean interrupted = ST_CONVERTING.equals(snapshot.getConvertStatus())
                || ST_VECTORIZING.equals(snapshot.getIndexStatus());
        if (interrupted) {
            RuntimeException cleanupFailure = null;
            try {
                vectorStorage.deleteDocument(documentUuid);
                deleteObjectIfUnreferenced(
                        snapshot.getMarkdownFilePath(),
                        documentUuid,
                        "RECOVER_DELETE_CONVERTED",
                        true);
            } catch (RuntimeException failure) {
                cleanupFailure = failure;
            }
            knowledgeFileMapper.markInterruptedProcessingFailed(
                    documentUuid, "后端进程重启，自动恢复文件处理");
            if (cleanupFailure != null) {
                throw cleanupFailure;
            }
            snapshot = knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        }

        if (snapshot == null || snapshot.getDeletedAt() != null
                || snapshot.getDeletionStatus() != null) {
            return;
        }
        if (ST_CONVERTED.equals(snapshot.getConvertStatus())) {
            int actualCount = vectorStorage.getVectorStatus(documentUuid);
            Integer expectedCount = snapshot.getChunkCount();
            if (ST_VECTORIZED.equals(snapshot.getIndexStatus())
                    && expectedCount != null && expectedCount > 0
                    && actualCount == expectedCount) {
                return;
            }
            vectorStorage.deleteDocument(documentUuid);
            if (knowledgeFileMapper.markIndexInconsistent(
                    documentUuid,
                    "数据库与 Chroma chunk 数不一致，自动重建") != 1) {
                throw persistenceFailure(
                        "MARK_INDEX_INCONSISTENT",
                        "知识文件无法进入索引重建状态");
            }
            retry(documentUuid);
            return;
        }

        // 历史转换失败可能已预留 converted key；重试前先幂等清理。
        deleteObjectIfUnreferenced(
                snapshot.getMarkdownFilePath(),
                documentUuid,
                "RECOVER_DELETE_CONVERTED",
                true);
        retry(documentUuid);
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
                vectorStorage.deleteDocument(documentUuid);
            } catch (AppException failure) {
                throw deleteFailure(
                        "DELETE_VECTORS", "知识文件向量删除失败", failure);
            }
            deleteObjectIfUnreferenced(
                    record.getOriginalFilePath(),
                    documentUuid,
                    "DELETE_OBJECT_ORIGINAL",
                    false);
            deleteObjectIfUnreferenced(
                    record.getMarkdownFilePath(),
                    documentUuid,
                    "DELETE_OBJECT_CONVERTED",
                    true);
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
        KnowledgeObjectKeyGenerator.OriginalObjectKey originalKey;
        try {
            originalKey = keyGenerator.original(
                    username,
                    UUID.fromString(documentUuid),
                    upload.originalFilename());
        } catch (IllegalArgumentException failure) {
            insertRecord(documentUuid, upload, "");
            return markIngestFailure(
                    documentUuid,
                    new AppException(
                            AppErrorCode.KNOWLEDGE_FILE_INVALID_FILE,
                            "knowledge-file",
                            "INGEST",
                            "BUILD_ORIGINAL_KEY",
                            "知识文件名不合法",
                            failure),
                    STAGE_UPLOAD);
        }
        insertRecord(documentUuid, upload, originalKey.objectKey());

        String stage = STAGE_UPLOAD;
        OriginalFile original;
        try {
            original = storeOriginal(documentUuid, upload, originalKey);
        } catch (AppException failure) {
            return markIngestFailure(documentUuid, failure, stage);
        } catch (RuntimeException failure) {
            return markIngestFailure(documentUuid, failure, stage);
        }

        try {
            updateOriginalMetadata(documentUuid, original);
        } catch (AppException failure) {
            cleanupUncommittedOriginalObject(documentUuid, original, failure);
            return markIngestFailure(documentUuid, failure, stage);
        } catch (RuntimeException failure) {
            cleanupUncommittedOriginalObject(documentUuid, original, failure);
            return markIngestFailure(documentUuid, failure, stage);
        }

        try {
            recordHistorySafely(
                    documentUuid,
                    OperationType.CREATE,
                    null,
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                    null);
            publishStatus(documentUuid);
            // 单元测试直接构造 Service 时没有 Spring 代理，回退为同步执行；
            // 正式运行由 self 代理触发 @Async。
            CurrentAccountContext.CurrentAccount operator = CurrentAccountContext.get();
            if (self == null) {
                processStoredFileAsync(documentUuid, operator);
            } else {
                self.processStoredFileAsync(documentUuid, operator);
            }
            return toQueuedResponse(
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid));
        } catch (AppException failure) {
            return markIngestFailure(documentUuid, failure, stage);
        } catch (RuntimeException failure) {
            return markIngestFailure(documentUuid, failure, stage);
        }
    }

    /**
     * 原始对象成功落库后，在后台完成转换和向量化，不阻塞上传请求。
     *
     * <p>操作人上下文是请求线程的 ThreadLocal；调度时显式传入快照，只在此后台任务运行期
     * 恢复，避免操作历史落库时失去上传人的账号归属。</p>
     */
    @Async("fileProcessingExecutor")
    public void processStoredFileAsync(
            String documentUuid,
            CurrentAccountContext.CurrentAccount operator) {
        CurrentAccountContext.CurrentAccount previous = CurrentAccountContext.get();
        try {
            if (operator == null) {
                CurrentAccountContext.clear();
            } else {
                CurrentAccountContext.set(operator);
            }
            processStoredFile(documentUuid);
        } finally {
            if (previous == null) {
                CurrentAccountContext.clear();
            } else {
                CurrentAccountContext.set(previous);
            }
        }
    }

    private void processStoredFile(String documentUuid) {
        String stage = STAGE_CONVERT;
        try {
            KnowledgeFileRecord record = loadActive(documentUuid);
            markConverting(documentUuid);
            KnowledgeFileRecord beforeConvert =
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid);
            PreparedKnowledgeDocument prepared = convertFromRecord(record);
            try {
                markConverted(documentUuid, prepared);
            } catch (RuntimeException failure) {
                cleanupUncommittedConvertedObject(
                        documentUuid, prepared, failure);
                throw failure;
            }
            recordHistorySafely(
                    documentUuid,
                    OperationType.UPDATE,
                    beforeConvert,
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                    null);

            stage = STAGE_VECTORIZE;
            markVectorizing(documentUuid);
            KnowledgeFileRecord beforeIndex =
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid);
            int chunkCount = vectorStorage.vectorizeDocument(prepared);
            markVectorized(documentUuid, chunkCount);
            recordHistorySafely(
                    documentUuid,
                    OperationType.UPDATE,
                    beforeIndex,
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                    null);
        } catch (AppException failure) {
            if (!hasDeletionStarted(documentUuid)) {
                markIngestFailure(documentUuid, failure, stage);
            }
        } catch (RuntimeException failure) {
            if (!hasDeletionStarted(documentUuid)) {
                markIngestFailure(documentUuid, failure, stage);
            }
        }
    }

    private OriginalFile storeOriginal(
            String documentUuid,
            KnowledgeFileUpload upload,
            KnowledgeObjectKeyGenerator.OriginalObjectKey key) {
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
        try {
            response = requestVerifiedMarkdown(
                    originalFile.storedObject(),
                    originalFile.safeOriginalFilename(),
                    originalFile.upload().contentType(),
                    originalFile.storedObject().fileSize(),
                    "INGEST");
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
                UUID.fromString(documentUuid),
                originalFile.safeOriginalFilename(),
                response.filename());
        requireRow(
                knowledgeFileMapper.reserveMarkdownPath(
                        documentUuid, convertedKey),
                "RESERVE_MARKDOWN_PATH",
                "知识文件 Markdown 路径预留失败");
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
        try {
            response = requestVerifiedMarkdown(
                    original,
                    record.getOriginalFilename(),
                    record.getContentType(),
                    record.getFileSize() == null ? 0 : record.getFileSize(),
                    "RETRY");
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
                UUID.fromString(record.getDocumentUuid()),
                safeFilenameFromStored(storedFilename(record.getOriginalFilePath())),
                response.filename());
        requireRow(
                knowledgeFileMapper.reserveMarkdownPath(
                        record.getDocumentUuid(), convertedKey),
                "RESERVE_MARKDOWN_PATH",
                "知识文件 Markdown 路径预留失败");
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
        verifyObjectSha256(
                record.getConvertedSha256(),
                markdown,
                "RETRY",
                "VERIFY_CONVERTED",
                "知识文件 Markdown 完整性校验失败");
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

    private void insertRecord(
            String documentUuid,
            KnowledgeFileUpload upload,
            String reservedOriginalObjectKey) {
        KnowledgeFileRecord record = new KnowledgeFileRecord();
        record.setDocumentUuid(documentUuid);
        record.setOriginalFilename(upload.originalFilename());
        record.setContentType(upload.contentType());
        record.setFileSize(upload.fileSize());
        record.setStorageType(storageProperties.getType().name());
        record.setOriginalFilePath(reservedOriginalObjectKey);
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
        if (hasDeletionStarted(documentUuid)) {
            KnowledgeFileRecord deleting =
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid);
            return toFailureResponse(
                    deleting, documentUuid, "知识文件正在删除或等待删除重试");
        }
        try {
            KnowledgeFileRecord current =
                    knowledgeFileMapper.selectByDocumentUuid(documentUuid);
            if (STAGE_UPLOAD.equals(stage)
                    && (current == null
                    || !"STORED".equals(current.getOriginalStorageStatus()))) {
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
        recordHistorySafely(
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
            Throwable failure,
            String stage) {
        if (hasDeletionStarted(documentUuid)) {
            return;
        }
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
        recordHistorySafely(
                documentUuid,
                OperationType.UPDATE,
                null,
                knowledgeFileMapper.selectByDocumentUuid(documentUuid),
                message);
        publishStatus(documentUuid);
    }

    /**
     * 从对象存储读取原件、发送给转换服务，并在流消费完成后校验数据库保存的 SHA-256。
     *
     * <p>初次存储的回读校验只能证明“写入当时”一致；重试会再次从 MinIO/本地存储读取，
     * 因此必须在接受转换结果、写 Markdown 和推进状态前确认本次读取的原始内容仍然对应
     * 文件记录。</p>
     */
    private PythonMarkdownResponse requestVerifiedMarkdown(
            StoredKnowledgeObject original,
            String filename,
            String contentType,
            long contentLength,
            String operation) {
        MessageDigest digest = createSha256Digest();
        PythonMarkdownResponse response;
        try (InputStream input = objectService.open(original);
             DigestInputStream verifiedInput = new DigestInputStream(input, digest)) {
            response = toMarkdownClient.convert(
                    verifiedInput, filename, contentType, contentLength);
        } catch (IOException failure) {
            throw storageFailure("READ_ORIGINAL", "知识文件原始内容读取失败", failure);
        }
        verifyObjectSha256(
                original.sha256(),
                HexFormat.of().formatHex(digest.digest()),
                operation,
                "VERIFY_ORIGINAL",
                "知识文件原始内容完整性校验失败");
        return response;
    }

    private void verifyObjectSha256(
            String expectedSha256,
            byte[] content,
            String operation,
            String stage,
            String message) {
        verifyObjectSha256(
                expectedSha256,
                HexFormat.of().formatHex(createSha256Digest().digest(content)),
                operation,
                stage,
                message);
    }

    private void verifyObjectSha256(
            String expectedSha256,
            String actualSha256,
            String operation,
            String stage,
            String message) {
        if (expectedSha256 == null
                || !expectedSha256.matches("(?i)[0-9a-f]{64}")
                || !expectedSha256.equalsIgnoreCase(actualSha256)) {
            throw new AppException(
                    AppErrorCode.KNOWLEDGE_FILE_INTEGRITY_CHECK_FAILED,
                    "knowledge-file",
                    operation,
                    stage,
                    message);
        }
    }

    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", failure);
        }
    }

    /**
     * 操作历史用于审计，不是转换或向量化事实状态的一部分。状态已成功落库后，
     * 历史表异常只能记录日志，不能把 CONVERTED/VECTORIZED 反向改写为 FAILED。
     */
    private void recordHistorySafely(
            String documentUuid,
            OperationType operation,
            KnowledgeFileRecord before,
            KnowledgeFileRecord after,
            String remark) {
        try {
            operationHistoryService.recordKnowledgeFile(
                    documentUuid, operation, before, after, remark);
        } catch (RuntimeException historyFailure) {
            log.error(
                    "知识文件状态已落库，但操作历史写入失败: documentUuid={}, "
                            + "operation={}",
                    documentUuid,
                    operation,
                    historyFailure);
        }
    }

    private void publishStatus(String documentUuid) {
        if (fileStatusEventPublisher == null) return;
        KnowledgeFileRecord latest = knowledgeFileMapper.selectByDocumentUuid(
                documentUuid);
        if (latest == null) return;
        fileStatusEventPublisher.publish(
                FileStatusEventPublisher.KNOWLEDGE_TOPIC,
                latest.getDocumentUuid(),
                latest.getOriginalStorageStatus(),
                latest.getConvertStatus(),
                latest.getIndexStatus());
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

    /** 删除已开始后，后台任务不得再推进文件状态或写入派生对象。 */
    private boolean hasDeletionStarted(String documentUuid) {
        KnowledgeFileRecord latest =
                knowledgeFileMapper.selectByDocumentUuidIncludingDeleted(documentUuid);
        return latest != null && latest.getDeletionStatus() != null;
    }

    /**
     * 兼容旧版本的同 key 数据：仍有其他 active 记录引用时不删除该对象。
     */
    private void deleteObjectIfUnreferenced(
            String objectKey,
            String documentUuid,
            String stage,
            boolean converted) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        int references = converted
                ? knowledgeFileMapper.countActiveByMarkdownFilePathExcluding(
                        objectKey, documentUuid)
                : knowledgeFileMapper.countActiveByOriginalFilePathExcluding(
                        objectKey, documentUuid);
        if (references > 0) {
            log.warn(
                    "保留仍被其他 active 知识文件引用的历史对象: documentUuid={}, path={}, references={}",
                    documentUuid,
                    objectKey,
                    references);
            return;
        }
        deleteObject(objectKey, documentUuid, stage);
    }

    /**
     * 原始对象已成功写入但数据库元数据尚未成功回填时，立即尝试删除，避免对象脱离记录。
     */
    private void cleanupUncommittedOriginalObject(
            String documentUuid,
            OriginalFile original,
            RuntimeException originalFailure) {
        if (original == null || original.storedObject() == null
                || original.storedObject().objectKey() == null
                || original.storedObject().objectKey().isBlank()) {
            return;
        }
        String objectKey = original.storedObject().objectKey();
        int references = knowledgeFileMapper.countActiveByOriginalFilePathExcluding(
                objectKey, documentUuid);
        if (references > 0) {
            log.warn(
                    "保留仍被其他 active 知识文件引用的未提交 original 对象: documentUuid={}, path={}, references={}",
                    documentUuid,
                    objectKey,
                    references);
            return;
        }
        try {
            objectService.delete(objectKey);
        } catch (IOException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            log.error(
                    "知识文件原始对象元数据回填失败后的补偿删除失败: documentUuid={}, path={}",
                    documentUuid,
                    objectKey,
                    cleanupFailure);
        }
    }

    /**
     * converted 对象已校验写入而状态回填失败时，删除尚未被任何 active 记录引用的对象。
     */
    private void cleanupUncommittedConvertedObject(
            String documentUuid,
            PreparedKnowledgeDocument prepared,
            RuntimeException originalFailure) {
        if (prepared == null || prepared.convertedObjectKey() == null
                || prepared.convertedObjectKey().isBlank()) {
            return;
        }
        String objectKey = prepared.convertedObjectKey();
        int references = knowledgeFileMapper.countActiveByMarkdownFilePathExcluding(
                objectKey, documentUuid);
        if (references > 0) {
            log.warn(
                    "保留仍被其他 active 知识文件引用的未提交 Markdown: documentUuid={}, path={}, references={}",
                    documentUuid,
                    objectKey,
                    references);
            return;
        }
        try {
            objectService.delete(objectKey);
        } catch (IOException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            log.error(
                    "知识文件 Markdown 状态回填失败后的补偿删除失败: documentUuid={}, path={}",
                    documentUuid,
                    objectKey,
                    cleanupFailure);
        }
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
                "知识文件正在处理或删除中，请稍后重试");
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
