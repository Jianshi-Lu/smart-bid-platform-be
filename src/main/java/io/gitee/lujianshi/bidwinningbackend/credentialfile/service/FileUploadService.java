package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatchContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileBatchMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.UploadItemProcessor;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.VerifiedStoredFileMetadata;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 资信文件上传批次编排 Service。
 *
 * <p>负责解析批次上下文、按请求顺序处理文件、收集验证结果和执行批次级失败补偿；
 * 单文件校验和计算、持久化及回读验证由 {@link UploadItemProcessor} 完成。</p>
 *
 * @since 1.0
 */
@Service
public class FileUploadService {

    private static final int MAX_BATCH_NO_RETRY = 3;

    @Resource
    private UploadItemProcessor uploadItemProcessor;

    @Resource
    private CredentialFileBatchMapper credentialFileBatchMapper;

    @Resource
    private FileStorage fileStorage;

    @Resource
    private io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper credentialFileMapper;

    @Resource
    private FileStorageProperties storageProperties;

    /**
     * 处理一次请求中的全部原始资信文件。
     *
     * <p>批次 UUID 在本方法开始时由后端生成并写入批次表。任一条目失败时，当前文件由处理器
     * 补偿，本服务删除本次请求中此前已经验证成功的文件；只有全部文件成功后才封口批次。</p>
     *
     * @param batch Controller 转换后的内部上传任务
     * @return 上传批次号和文件持久化结果
     * @throws AppException 上传请求、存储、完整性校验或数据库持久化失败
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadResponse upload(UploadBatch batch) {
        if (batch == null) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "UPLOAD",
                    "VALIDATE_REQUEST",
                    "上传批次不能为空");
        }
        CredentialFileBatch batchRecord = createUploadingBatch(
                batch.bidRecordId(), batch.items().size());
        UploadBatchContext context = new UploadBatchContext(
                batch.bidRecordId(), batchRecord.getUploadBatchId());

        List<String> processedPaths = new ArrayList<>();
        List<UploadResponse.UploadedFile> responseItems = new ArrayList<>();
        try {
            // 将每一个UploadBatch中的Item交给processor处理：
            // 1. 循环全部成功：返回所有成功存储的文件——controller
            // 2. 循环有一个失败：清空所有成功存储的文件，抛出异常
            for (int index = 0; index < batch.items().size(); index++) {
                UploadItem item = batch.items().get(index);
                VerifiedStoredFileMetadata verifiedMetadata =
                        uploadItemProcessor.process(context, item);
                processedPaths.add(verifiedMetadata.relativePath());
                credentialFileMapper.insert(toCredentialFile(
                        context,
                        index,
                        item,
                        verifiedMetadata));
                responseItems.add(toResponseItem(index, item, verifiedMetadata));
            }
            markUploaded(context.uploadBatchId());
        } catch (AppException failure) {
            cleanup(processedPaths, failure);
            throw failure;
        } catch (IllegalArgumentException failure) {
            cleanup(processedPaths, failure);
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_FILE,
                    "credential-file",
                    "UPLOAD",
                    "VALIDATE_FILE",
                    "上传文件不合法",
                    failure);
        } catch (IOException failure) {
            cleanup(processedPaths, failure);
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_STORAGE_FAILED,
                    "credential-file",
                    "UPLOAD",
                    "STORE_ORIGINAL",
                    "资信文件存储失败",
                    failure);
        } catch (RuntimeException failure) {
            cleanup(processedPaths, failure);
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_PERSISTENCE_FAILED,
                    "credential-file",
                    "UPLOAD",
                    "PERSIST_FILE_RECORD",
                    "资信文件保存失败",
                    failure);
        }

        return new UploadResponse(context.uploadBatchId(), responseItems);
    }

    /** 创建本次上传对应的批次记录，并分配投标记录下的批次序号。 */
    private CredentialFileBatch createUploadingBatch(
            Integer bidRecordId,
        int fileCount) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "UPLOAD",
                    "VALIDATE_REQUEST",
                    "投标记录 ID 不正确");
        }
        if (fileCount <= 0) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "UPLOAD",
                    "VALIDATE_REQUEST",
                    "上传文件不能为空");
        }

        for (int attempt = 1; attempt <= MAX_BATCH_NO_RETRY; attempt++) {
            Integer maxBatchNo = credentialFileBatchMapper.selectMaxBatchNo(bidRecordId);
            int nextBatchNo = maxBatchNo == null ? 1 : maxBatchNo + 1;

            CredentialFileBatch batch = new CredentialFileBatch();
            batch.setUploadBatchId(UUID.randomUUID().toString());
            batch.setBidRecordId(bidRecordId);
            batch.setBatchNo(nextBatchNo);
            batch.setStatus(ConvertStatus.UPLOADING);
            batch.setFileCount(fileCount);
            batch.setConvertedFileCount(0);
            batch.setCreatedAt(LocalDateTime.now());

            try {
                int rows = credentialFileBatchMapper.insert(batch);
                if (rows != 1) {
                    throw new AppException(
                            AppErrorCode.CREDENTIAL_FILE_PERSISTENCE_FAILED,
                            "credential-file",
                            "UPLOAD",
                            "CREATE_BATCH",
                            "资信文件上传批次创建失败");
                }
                return batch;
            } catch (DuplicateKeyException exception) {
                if (attempt == MAX_BATCH_NO_RETRY) {
                    throw new AppException(
                            AppErrorCode.CREDENTIAL_FILE_PERSISTENCE_FAILED,
                            "credential-file",
                            "UPLOAD",
                            "CREATE_BATCH",
                            "资信文件上传批次创建失败",
                            exception);
                }
            }
        }
        throw new AppException(
                AppErrorCode.CREDENTIAL_FILE_PERSISTENCE_FAILED,
                "credential-file",
                "UPLOAD",
                "CREATE_BATCH",
                "资信文件上传批次创建失败");
    }

    /** 全部原始文件成功落库后，将批次封口为已上传。 */
    private void markUploaded(String uploadBatchId) {
        if (uploadBatchId == null || uploadBatchId.isBlank()) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "UPLOAD",
                    "MARK_UPLOADED",
                    "上传批次号不能为空");
        }
        int rows = credentialFileBatchMapper.markUploaded(
                uploadBatchId,
                ConvertStatus.UPLOADED.name(),
                LocalDateTime.now());
        if (rows != 1) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_PERSISTENCE_FAILED,
                    "credential-file",
                    "UPLOAD",
                    "MARK_UPLOADED",
                    "资信文件上传批次封口失败");
        }
    }

    /** 将已经验证的存储结果转换为对外响应条目。 */
    private UploadResponse.UploadedFile toResponseItem(
            int fileOrder,
            UploadItem item,
            VerifiedStoredFileMetadata verifiedMetadata) {
        String contentType = verifiedMetadata.contentType() == null
                ? item.contentType()
                : verifiedMetadata.contentType();
        return new UploadResponse.UploadedFile(
                fileOrder,
                verifiedMetadata.safeOriginalFilename(),
                contentType,
                verifiedMetadata.fileSize(),
                verifiedMetadata.sha256(),
                verifiedMetadata.relativePath(),
                ConvertStatus.UPLOADED);
    }

    /** 将已通过存储完整性校验的文件写入 credential_file。 */
    private CredentialFile toCredentialFile(
            UploadBatchContext context,
            int fileOrder,
            UploadItem item,
            VerifiedStoredFileMetadata verifiedMetadata) {
        CredentialFile file = new CredentialFile();
        file.setUploadBatchId(context.uploadBatchId());
        file.setBidRecordId(context.bidRecordId());
        file.setFileOrder(fileOrder);
        file.setOriginalFilename(verifiedMetadata.safeOriginalFilename());
        file.setContentType(verifiedMetadata.contentType() == null
                ? item.contentType()
                : verifiedMetadata.contentType());
        file.setFileSize(verifiedMetadata.fileSize());
        file.setSha256(verifiedMetadata.sha256());
        file.setStorageType(storageProperties.getType().name());
        file.setOriginalFilePath(verifiedMetadata.relativePath());
        file.setStatus(ConvertStatus.UPLOADED);
        file.setCreatedAt(LocalDateTime.now());
        file.setBoundAt(LocalDateTime.now());
        return file;
    }

    /** 按逆序尽力删除本次请求此前已验证成功的文件。 */
    private void cleanup(
            List<String> processedPaths,
            Throwable originalFailure) {
        for (String relativePath : processedPaths.reversed()) {
            try {
                fileStorage.delete(relativePath);
            } catch (IOException | RuntimeException cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }
    }
}
