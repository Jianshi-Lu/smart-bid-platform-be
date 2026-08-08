package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadFileContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.UploadItemProcessor;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.VerifiedStoredFileMetadata;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
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
 * 留痕（FAILED + error_stage + error_message），不整体回滚、不删除已存对象、不连坐
 * 同批其他文件；存储成功后立即同步转换，响应逐文件返回最新状态。</p>
 *
 * @since 1.0
 */
@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(
            FileUploadService.class);

    /** 上传阶段失败标记（对齐 knowledge_file error_stage）。 */
    private static final String STAGE_UPLOAD = "UPLOAD";
    /** 删除终态标记（与 V45 deletion_status 对齐）。 */
    private static final String DELETION_DELETED = "DELETED";
    /** DELETING 超过该时长视为进程崩溃残留，允许其他请求抢占续删。 */
    private static final long DELETE_STALE_SECONDS = 10 * 60;

    @Resource
    private UploadItemProcessor uploadItemProcessor;

    @Resource
    private FileStorage fileStorage;

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
        CredentialFile file = new CredentialFile();
        file.setFileUuid(fileUuid);
        file.setBidRecordId(bidRecordId);
        file.setFileOrder(fileOrder);
        file.setOriginalFilename(item.originalFilename());
        file.setContentType(item.contentType());
        file.setFileSize(item.fileSize());
        file.setStorageType(storageProperties.getType().name());
        file.setOriginalFilePath("");
        file.setOriginalStorageStatus("STORING");
        file.setConvertStatus(ConvertStatus.UPLOADED);
        file.setCreatedAt(LocalDateTime.now());
        file.setBoundAt(LocalDateTime.now());
        if (credentialFileMapper.insert(file) != 1) {
            throw persistenceFailure("INSERT", "资信文件记录创建失败");
        }

        try {
            UploadFileContext context = new UploadFileContext(
                    bidRecordId,
                    bidderAccountId,
                    username,
                    fileUuid);
            VerifiedStoredFileMetadata verified =
                    uploadItemProcessor.process(context, item);
            updateOriginalMetadata(file.getFileId(), verified);
            operationHistoryService.recordCredentialFile(
                    file.getFileId(),
                    OperationType.CREATE,
                    null,
                    credentialFileMapper.selectById(file.getFileId()),
                    null);
            publishStorageStatus(file.getFileId());
            // 原文件已经通过存储回读校验，转换进入后台线程，不阻塞上传响应。
            fileConvertService.convertFileAsync(file.getFileId());
            return toResponseItem(
                    credentialFileMapper.selectById(file.getFileId()),
                    fileOrder);
        } catch (IOException | RuntimeException failure) {
            return markIngestFailure(
                    file.getFileId(), fileOrder, STAGE_UPLOAD, failure);
        }
    }

    /**
     * 删除指定投标记录下的一个资信文件：可追溯、可幂等续删的状态机。
     *
     * <p>先原子抢占 DELETING，再删 original/converted 对象、软删记录、写删除历史
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
                fileConvertService.syncBidRecordCredentials(bidRecordId);
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
                    fileConvertService.syncBidRecordCredentials(bidRecordId);
                    return;
                }
                completeDeletionHistory(fileId, bidRecordId, latest);
                return;
            }
            throw deleteInProgress(fileId);
        }

        try {
            deleteStoredObject(
                    file.getOriginalFilePath(),
                    fileId,
                    "DELETE_OBJECT_ORIGINAL");
            if (file.getMarkdownFilePath() != null
                    && !file.getMarkdownFilePath().isBlank()) {
                deleteStoredObject(
                        file.getMarkdownFilePath(),
                        fileId,
                        "DELETE_OBJECT_CONVERTED");
            }
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
            fileConvertService.syncBidRecordCredentials(bidRecordId);
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
            fileConvertService.syncBidRecordCredentials(bidRecordId);
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

    /** 单文件失败留痕：FAILED + error_stage + error_message，并写历史后返回失败条目。 */
    private UploadResponse.UploadedFile markIngestFailure(
            Long fileId,
            int fileOrder,
            String stage,
            Throwable failure) {
        String message = toErrorMessage(failure);
        try {
            if (STAGE_UPLOAD.equals(stage)) {
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
                file.getErrorMessage(),
                file.getErrorStage());
    }

    private AppException persistenceFailure(String stage, String message) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_PERSISTENCE_FAILED,
                "credential-file",
                "UPLOAD",
                stage,
                message);
    }

    private void publishStorageStatus(Long fileId) {
        CredentialFile latest = credentialFileMapper.selectById(fileId);
        if (latest == null || fileStatusEventPublisher == null) return;
        fileStatusEventPublisher.publish(
                FileStatusEventPublisher.CREDENTIAL_TOPIC_PREFIX
                        + latest.getBidRecordId(),
                String.valueOf(latest.getFileId()),
                latest.getConvertStatus() == null
                        ? null : latest.getConvertStatus().name(),
                null,
                latest.getOriginalStorageStatus());
    }
}
