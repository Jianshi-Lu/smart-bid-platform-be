package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadFileContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.UploadItemProcessor;
import io.gitee.lujianshi.bidwinningbackend.storage.object.ObjectStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.VerifiedStoredFileMetadata;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import io.gitee.lujianshi.bidwinningbackend.storage.vector.CredentialFileVectorStorage;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 资信文件上传编排 Service。
 *
 * <p>资信文件强制绑定投标方账号：每个文件独立生成文件单元 UUID，路径以账号为顶层归属；
 * 上传与转换流程对齐知识文件链路（向量化除外）：先落库再存储，失败只在单文件记录上
 * 留痕（FAILED + error_stage + error_message），不整体回滚、不连坐同批其他文件；原始对象
 * 经回读校验后必须先回填数据库元数据，元数据回填失败则立即补偿删除该对象，避免留下无记录
 * 可追溯的孤儿对象。原始对象持久化完成后触发后台转换，上传响应返回提交时的持久化状态。</p>
 *
 * @since 1.0
 */
@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(
            FileUploadService.class);

    /** 上传阶段失败标记（对齐 knowledge_file error_stage）。 */
    private static final String STAGE_UPLOAD = "UPLOAD";
    /** 原件已落库但异步转换调度失败时的失败阶段。 */
    private static final String STAGE_CONVERT = "CONVERT";
    /** 删除终态标记（与 V45 deletion_status 对齐）。 */
    private static final String DELETION_DELETED = "DELETED";
    /** DELETING 超过该时长视为进程崩溃残留，允许其他请求抢占续删。 */
    private static final long DELETE_STALE_SECONDS = 10 * 60;

    @Resource
    private UploadItemProcessor uploadItemProcessor;

    @Resource
    private ObjectStorage fileStorage;

    @Resource
    private io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper credentialFileMapper;

    @Resource
    private FileStorageProperties storageProperties;

    @Resource
    private io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileConvertService fileConvertService;

    @Resource
    private OperationHistoryService operationHistoryService;

    @Resource
    private FileStatusEventPublisher fileStatusEventPublisher;

    @Resource
    private CredentialFileVectorStorage credentialVectorStorage;

    /**
     * 处理一次请求中的全部原始资信文件，每个文件独立成文件单元。
     *
     * <p>文件单元 UUID 由后端生成并同时作为 {@code credential_file.file_uuid}；
     * 单个文件失败不影响同批其他文件，失败文件在响应中返回 FAILED 状态。</p>
     *
     * @param bidRecordId 文件所属的投标记录 ID
     * @param bidderAccountId 投标方账号 ID，必须与投标记录归属一致
     * @param username 投标方登录用户名（对象存储路径顶层归属）
     * @param items 本次请求中等待处理的文件条目
     * @return 文件持久化结果
     * @throws AppException 仅请求边界校验失败时抛出；单文件处理失败不抛
     */
    public UploadResponse upload(
            Integer bidRecordId,
            Long bidderAccountId,
            String username,
            List<UploadItem> items) {
        validateRequest(bidRecordId, bidderAccountId, username, items);

        List<UploadResponse.UploadedFile> responseItems = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            responseItems.add(ingestOne(
                    bidRecordId,
                    bidderAccountId,
                    username,
                    index,
                    items.get(index)));
        }
        return new UploadResponse(responseItems);
    }

    /** 单个资信文件独立入库：落库 → 存储 → 回填元数据 → 异步转换 → 返回最新状态。 */
    private UploadResponse.UploadedFile ingestOne(
            Integer bidRecordId,
            Long bidderAccountId,
            String username,
            int fileOrder,
            UploadItem item) {
        String fileUuid = UUID.randomUUID().toString();
        StoragePath.OriginalFilePath reservedPath = null;
        RuntimeException pathFailure = null;
        try {
            reservedPath = StoragePath.createOriginalFilePath(
                    username, fileUuid, item.originalFilename());
        } catch (IllegalArgumentException failure) {
            pathFailure = new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_PATH,
                    "credential-file",
                    "UPLOAD",
                    "BUILD_STORAGE_PATH",
                    "资信文件存储路径不合法",
                    failure);
        }
        CredentialFile file = new CredentialFile();
        file.setFileUuid(fileUuid);
        file.setBidRecordId(bidRecordId);
        file.setFileOrder(fileOrder);
        file.setOriginalFilename(item.originalFilename());
        file.setContentType(item.contentType());
        file.setFileSize(item.fileSize());
        file.setStorageType(storageProperties.getType().name());
        // 先保存最终 object key，再开始外部存储；JVM 在 store 与元数据回填之间退出时，
        // 重启治理仍能按该路径幂等删除未提交对象。
        file.setOriginalFilePath(
                reservedPath == null ? "" : reservedPath.relativePath());
        file.setOriginalStorageStatus("STORING");
        file.setConvertStatus(ConvertStatus.UPLOADED);
        file.setIndexStatus("PENDING");
        file.setCreatedAt(LocalDateTime.now());
        file.setBoundAt(LocalDateTime.now());
        if (credentialFileMapper.insert(file) != 1) {
            throw persistenceFailure("INSERT", "资信文件记录创建失败");
        }
        if (pathFailure != null) {
            return markIngestFailure(
                    file.getFileId(), fileOrder, STAGE_UPLOAD, pathFailure);
        }

        VerifiedStoredFileMetadata verified;
        try {
            UploadFileContext context = new UploadFileContext(
                    bidRecordId,
                    bidderAccountId,
                    username,
                    fileUuid);
            verified = uploadItemProcessor.process(context, item);
        } catch (IOException | RuntimeException failure) {
            return markIngestFailure(
                    file.getFileId(), fileOrder, STAGE_UPLOAD, failure);
        }

        try {
            updateOriginalMetadata(file.getFileId(), verified);
        } catch (RuntimeException failure) {
            cleanupUncommittedOriginalObject(
                    file.getFileId(), verified.relativePath(), failure);
            return markIngestFailure(
                    file.getFileId(), fileOrder, STAGE_UPLOAD, failure);
        }

        try {
            publishStorageStatus(file.getFileId());
            recordUploadHistorySafely(
                    file.getFileId(),
                    credentialFileMapper.selectById(file.getFileId()));
            // 原文件已经通过存储回读校验，转换进入后台线程，不阻塞上传响应。
            fileConvertService.convertFileAsync(
                    file.getFileId(), CurrentAccountContext.get());
            return toResponseItem(
                    credentialFileMapper.selectById(file.getFileId()),
                    fileOrder);
        } catch (RuntimeException failure) {
            return markIngestFailure(
                    file.getFileId(), fileOrder, STAGE_CONVERT, failure);
        }
    }

    /**
     * 删除指定投标记录下的一个资信文件：可追溯、可幂等续删的状态机。
     *
     * <p>先原子抢占 DELETING，再删 Chroma 向量、original/converted 对象、软删记录、写删除历史
     * 并同步 {@code bid_record.credentials} 投影；任一步失败都会把失败点与原因
     * 落库后抛错，记录保持 active，再次调用本方法会从头幂等重放并收敛。
     * 历史写入失败导致已软删但未终态时，重试走补记历史分支。</p>
     *
     * @param bidRecordId 文件所属的投标记录 ID
     * @param fileId 资信文件 ID
     */
    public void delete(Integer bidRecordId, Long fileId) {
        if (bidRecordId == null || bidRecordId <= 0 || fileId == null || fileId <= 0) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "DELETE",
                    "VALIDATE_REQUEST",
                    "资信文件删除参数不正确");
        }
        CredentialFile file =
                credentialFileMapper.selectByIdIncludingDeleted(fileId);
        if (file == null || !Objects.equals(file.getBidRecordId(), bidRecordId)) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_NOT_FOUND,
                    "credential-file",
                    "DELETE",
                    "LOAD_FILE",
                    "资信文件不存在: " + fileId);
        }
        if (file.getDeletedAt() != null) {
            if (DELETION_DELETED.equals(file.getDeletionStatus())) {
                fileConvertService.syncBidRecordCredentialsAfterDeletionSafely(
                        bidRecordId, fileId);
                return;
            }
            completeDeletionHistory(fileId, bidRecordId, file);
            return;
        }

        LocalDateTime startedAt = LocalDateTime.now();
        int claimed = credentialFileMapper.beginDeletion(
                fileId,
                startedAt,
                startedAt.minusSeconds(DELETE_STALE_SECONDS));
        if (claimed != 1) {
            CredentialFile latest =
                    credentialFileMapper.selectByIdIncludingDeleted(fileId);
            if (latest != null && latest.getDeletedAt() != null) {
                if (DELETION_DELETED.equals(latest.getDeletionStatus())) {
                    fileConvertService.syncBidRecordCredentialsAfterDeletionSafely(
                            bidRecordId, fileId);
                    return;
                }
                completeDeletionHistory(fileId, bidRecordId, latest);
                return;
            }
            throw deleteInProgress(fileId);
        }

        try {
            try {
                credentialVectorStorage.deleteDocument(file.getFileUuid());
            } catch (AppException failure) {
                throw deleteFailure(
                        "DELETE_VECTORS", "资信文件向量删除失败", failure);
            }
            invalidateMergedMarkdown(file, fileId);
            deleteStoredObjectIfUnreferenced(
                    file.getOriginalFilePath(),
                    fileId,
                    "DELETE_OBJECT_ORIGINAL",
                    false);
            deleteStoredObjectIfUnreferenced(
                    file.getMarkdownFilePath(),
                    fileId,
                    "DELETE_OBJECT_CONVERTED",
                    true);
            if (credentialFileMapper.markDeleted(fileId, startedAt) != 1) {
                throw deleteFailure(
                        "MARK_DELETED", "资信文件记录软删除失败", null);
            }
            try {
                operationHistoryService.recordCredentialFile(
                        fileId,
                        OperationType.DELETE,
                        file,
                        null,
                        null);
            } catch (RuntimeException historyFailure) {
                markDeletionFailed(
                        fileId, "HISTORY", toErrorMessage(historyFailure));
                throw deleteFailure(
                        "HISTORY", "资信文件删除历史写入失败", historyFailure);
            }
            credentialFileMapper.markDeletionSucceeded(fileId);
            fileConvertService.syncBidRecordCredentialsAfterDeletionSafely(
                    bidRecordId, fileId);
        } catch (AppException failure) {
            markDeletionFailed(
                    fileId, failure.getStage(), toErrorMessage(failure));
            throw failure;
        } catch (RuntimeException failure) {
            markDeletionFailed(
                    fileId, "UNKNOWN", toErrorMessage(failure));
            throw failure;
        }
    }

    /** 查询指定投标记录下的全部未删除资信文件（按创建/顺序排列）。 */
    public List<CredentialFile> listByBidRecordId(Integer bidRecordId) {
        return credentialFileMapper.selectByBidRecordId(bidRecordId);
    }

    /** 启动对账时从 credential_file 事实行严格重算投标记录 credentials 投影。 */
    public void reconcileBidRecordProjection(Integer bidRecordId) {
        fileConvertService.syncBidRecordCredentials(bidRecordId);
    }

    /**
     * JVM 重启后的单文件治理入口：续删、清理未提交上传，或委托转换服务完成状态对账。
     */
    public void reconcileLifecycle(CredentialFile snapshot) {
        if (snapshot == null || snapshot.getFileId() == null) {
            return;
        }
        Long fileId = snapshot.getFileId();
        Integer bidRecordId = snapshot.getBidRecordId();
        if (snapshot.getDeletedAt() != null
                && DELETION_DELETED.equals(snapshot.getDeletionStatus())) {
            return;
        }
        if (snapshot.getDeletedAt() != null
                || snapshot.getDeletionStatus() != null) {
            if ("DELETING".equals(snapshot.getDeletionStatus())) {
                credentialFileMapper.resetInterruptedDeletion(fileId);
            }
            delete(bidRecordId, fileId);
            return;
        }

        if ("STORING".equals(snapshot.getOriginalStorageStatus())
                || "FAILED".equals(snapshot.getOriginalStorageStatus())) {
            RuntimeException cleanupFailure = null;
            try {
                credentialVectorStorage.deleteDocument(snapshot.getFileUuid());
                deleteStoredObjectIfUnreferenced(
                        snapshot.getMarkdownFilePath(),
                        fileId,
                        "RECOVER_DELETE_CONVERTED",
                        true);
                deleteStoredObjectIfUnreferenced(
                        snapshot.getOriginalFilePath(),
                        fileId,
                        "RECOVER_DELETE_ORIGINAL",
                        false);
            } catch (RuntimeException failure) {
                cleanupFailure = failure;
            }
            if ("STORING".equals(snapshot.getOriginalStorageStatus())) {
                credentialFileMapper.markInterruptedProcessingFailed(
                        fileId, "后端进程重启，未提交上传已清理");
                publishStorageStatus(fileId);
            }
            if (cleanupFailure != null) {
                throw cleanupFailure;
            }
            return;
        }

        fileConvertService.reconcileActiveFile(fileId);
    }

    /**
     * 删除投标记录前清理其全部资信文件和记录级汇总 Markdown。
     *
     * <p>父记录只在本方法完整返回后才能物理删除；任一子文件删除失败时直接中止，父记录
     * 保持可重试，避免留下脱离投标记录的资信对象或表记录。</p>
     */
    public void deleteAllForBidRecord(Integer bidRecordId, String username) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "DELETE",
                    "VALIDATE_PARENT_DELETE",
                    "删除投标记录的资信文件参数不正确");
        }
        List<CredentialFile> files = credentialFileMapper.selectByBidRecordId(bidRecordId);
        if (files != null) {
            for (CredentialFile file : files) {
                if (file != null && file.getFileId() != null) {
                    delete(bidRecordId, file.getFileId());
                }
            }
        }
        if (username != null && !username.isBlank()) {
            deleteMergedMarkdown(bidRecordId, username, null);
        }
    }

    /**
     * 兼容历史同 key 数据：只有没有其他 active 文件引用对象时才物理删除。
     */
    private void deleteStoredObjectIfUnreferenced(
            String relativePath,
            Long fileId,
            String stage,
            boolean converted) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        int references = converted
                ? credentialFileMapper.countActiveByMarkdownFilePathExcluding(
                        relativePath, fileId)
                : credentialFileMapper.countActiveByOriginalFilePathExcluding(
                        relativePath, fileId);
        if (references > 0) {
            log.warn(
                    "保留仍被其他 active 资信文件引用的历史对象: fileId={}, path={}, references={}",
                    fileId,
                    relativePath,
                    references);
            return;
        }
        deleteStoredObject(relativePath, fileId, stage);
    }

    /** 删除任一资信文件时使包含该文件内容的记录级汇总 Markdown 立即失效。 */
    private void invalidateMergedMarkdown(CredentialFile file, Long fileId) {
        String originalPath = file.getOriginalFilePath();
        if (originalPath == null || originalPath.isBlank()) {
            return;
        }
        deleteMergedMarkdown(
                file.getBidRecordId(), usernameFromOriginalPath(originalPath), fileId);
    }

    private void deleteMergedMarkdown(
            Integer bidRecordId,
            String username,
            Long fileId) {
        try {
            String mergedPath = StoragePath.createMergedMarkdownPath(
                    bidRecordId, username);
            fileStorage.delete(mergedPath);
        } catch (IOException | RuntimeException failure) {
            throw deleteFailure(
                    "DELETE_OBJECT_MERGED",
                    "投标记录资信汇总对象删除失败"
                            + (fileId == null ? "" : ": " + fileId),
                    failure);
        }
    }

    private String usernameFromOriginalPath(String originalPath) {
        int separator = originalPath.indexOf('/');
        if (separator <= 0) {
            throw deleteFailure(
                    "RESOLVE_OBJECT_OWNER",
                    "资信文件原始对象路径缺少用户名目录",
                    null);
        }
        return originalPath.substring(0, separator);
    }

    private void deleteStoredObject(
            String relativePath,
            Long fileId,
            String stage) {
        try {
            fileStorage.delete(relativePath);
        } catch (IOException | RuntimeException failure) {
            throw deleteFailure(
                    stage, "资信文件存储对象删除失败: " + fileId, failure);
        }
    }

    /**
     * 原始对象已通过存储回读但数据库元数据未能提交时，立即尝试补偿，防止无人引用的对象残留。
     */
    private void cleanupUncommittedOriginalObject(
            Long fileId,
            String relativePath,
            RuntimeException originalFailure) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            fileStorage.delete(relativePath);
        } catch (IOException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            log.error(
                    "资信文件数据库元数据回填失败后，原始对象补偿删除失败: fileId={}, path={}",
                    fileId,
                    relativePath,
                    cleanupFailure);
        }
    }

    /** 已软删但未完成终态（历史未写入）时补记删除历史、置终态并刷新投影。 */
    private void completeDeletionHistory(
            Long fileId,
            Integer bidRecordId,
            CredentialFile file) {
        try {
            operationHistoryService.recordCredentialFile(
                    fileId,
                    OperationType.DELETE,
                    file,
                    null,
                    "删除历史补记（删除流程中断恢复）");
            credentialFileMapper.markDeletionSucceeded(fileId);
            fileConvertService.syncBidRecordCredentialsAfterDeletionSafely(
                    bidRecordId, fileId);
        } catch (RuntimeException historyFailure) {
            markDeletionFailed(
                    fileId, "HISTORY", toErrorMessage(historyFailure));
            throw deleteFailure(
                    "HISTORY", "资信文件删除历史补记失败", historyFailure);
        }
    }

    /** 删除失败落库：保留失败点与原因，供追溯与重试。 */
    private void markDeletionFailed(
            Long fileId,
            String stage,
            String errorMessage) {
        credentialFileMapper.markDeletionFailed(
                fileId, stage, errorMessage);
    }

    private AppException deleteFailure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_DELETE_FAILED,
                "credential-file",
                "DELETE",
                stage,
                message,
                cause);
    }

    private AppException deleteInProgress(Long fileId) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_DELETE_IN_PROGRESS,
                "credential-file",
                "DELETE",
                "IN_PROGRESS",
                "资信文件正在删除中，请稍后重试");
    }

    private String toErrorMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure == null
                    ? "未知错误"
                    : failure.getClass().getSimpleName();
        }
        return message.length() > 1000
                ? message.substring(0, 1000)
                : message;
    }

    private void validateRequest(
            Integer bidRecordId,
            Long bidderAccountId,
            String username,
            List<UploadItem> items) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "UPLOAD",
                    "VALIDATE_REQUEST",
                    "投标记录 ID 不正确");
        }
        if (bidderAccountId == null || bidderAccountId <= 0) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "UPLOAD",
                    "VALIDATE_REQUEST",
                    "资信文件必须绑定投标方账号");
        }
        if (username == null || username.isBlank()) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "UPLOAD",
                    "VALIDATE_REQUEST",
                    "资信文件必须绑定投标方用户名");
        }
        if (items == null || items.isEmpty()
                || items.stream().anyMatch(item -> item == null)) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "UPLOAD",
                    "VALIDATE_REQUEST",
                    "上传文件不能为空");
        }
    }

    /** 原始对象落库后回填路径、SHA-256 与存储介质（对齐 knowledge_file）。 */
    private void updateOriginalMetadata(
            Long fileId,
            VerifiedStoredFileMetadata verified) {
        int rows = credentialFileMapper.updateOriginalMetadata(
                fileId,
                verified.relativePath(),
                verified.sha256(),
                storageProperties.getType().name());
        if (rows != 1) {
            throw persistenceFailure(
                    "UPDATE_ORIGINAL", "资信文件原始对象元数据回填失败");
        }
    }

    /**
     * 审计历史属于原文件已校验落库后的派生写入，失败时不能把有效文件改写为 FAILED。
     */
    private void recordUploadHistorySafely(
            Long fileId,
            CredentialFile stored) {
        if (stored == null) {
            log.error(
                    "资信文件原件状态已落库，但无法读取记录以写入上传操作历史: fileId={}",
                    fileId);
            return;
        }
        try {
            operationHistoryService.recordCredentialFile(
                    stored.getFileId(), OperationType.CREATE, null, stored, null);
        } catch (RuntimeException historyFailure) {
            log.error(
                    "资信文件原件状态已落库，但上传操作历史写入失败: fileId={}",
                    fileId,
                    historyFailure);
        }
    }

    /** 单文件失败留痕：FAILED + error_stage + error_message，并写历史后返回失败条目。 */
    private UploadResponse.UploadedFile markIngestFailure(
            Long fileId,
            int fileOrder,
            String stage,
            Throwable failure) {
        String message = toErrorMessage(failure);
        try {
            CredentialFile current = credentialFileMapper.selectById(fileId);
            if (current != null && current.getDeletionStatus() != null) {
                return toResponseItem(current, fileOrder);
            }
            if (current == null
                    || !"STORED".equals(current.getOriginalStorageStatus())) {
                credentialFileMapper.markOriginalStorageFailed(fileId, message);
            }
            credentialFileMapper.markFailed(fileId, message, stage);
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "资信文件失败状态落库失败: fileId={}",
                    fileId,
                    persistenceFailure);
        }
        CredentialFile file = credentialFileMapper.selectById(fileId);
        publishStorageStatus(fileId);
        try {
            operationHistoryService.recordCredentialFile(
                    fileId,
                    OperationType.UPDATE,
                    null,
                    file,
                    message);
        } catch (RuntimeException historyFailure) {
            log.error(
                    "资信文件失败历史写入失败: fileId={}",
                    fileId,
                    historyFailure);
        }
        return toResponseItem(file, fileOrder);
    }

    /** 将最新数据库记录转换为对外响应条目（含失败原因与阶段）。 */
    private UploadResponse.UploadedFile toResponseItem(
            CredentialFile file,
            int fileOrder) {
        return new UploadResponse.UploadedFile(
                file.getFileId(),
                fileOrder,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getFileSize(),
                file.getOriginalSha256(),
                file.getOriginalFilePath(),
                file.getOriginalStorageStatus(),
                file.getConvertStatus(),
                file.getIndexStatus(),
                file.getChunkCount(),
                file.getIndexedAt(),
                file.getErrorMessage(),
                file.getErrorStage());
    }

    private void publishStorageStatus(Long fileId) {
        if (fileStatusEventPublisher == null) return;
        CredentialFile latest = credentialFileMapper.selectById(fileId);
        if (latest == null) return;
        fileStatusEventPublisher.publish(
                FileStatusEventPublisher.CREDENTIAL_TOPIC_PREFIX
                        + latest.getBidRecordId(),
                String.valueOf(latest.getFileId()),
                latest.getOriginalStorageStatus(),
                latest.getConvertStatus() == null
                        ? null
                        : latest.getConvertStatus().name(),
                latest.getIndexStatus());
    }

    private AppException persistenceFailure(String stage, String message) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_PERSISTENCE_FAILED,
                "credential-file",
                "UPLOAD",
                stage,
                message);
    }

}
