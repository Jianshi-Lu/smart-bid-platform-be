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
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 单文件校验和计算、持久化及回读验证由 {@link UploadItemProcessor} 完成，失败时整请求补偿。</p>
 *
 * @since 1.0
 */
@Service
public class FileUploadService {

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

    /**
     * 处理一次请求中的全部原始资信文件，每个文件独立成文件单元。
     *
     * <p>文件单元 UUID 由后端生成并同时作为 {@code credential_file.file_uuid}
     * 兼容列的值；任一条目失败时，本服务删除本次请求中此前已经验证成功的文件。</p>
     *
     * @param bidRecordId 文件所属的投标记录 ID
     * @param bidderAccountId 投标方账号 ID，必须与投标记录归属一致
     * @param items 本次请求中等待处理的文件条目
     * @return 文件持久化结果
     * @throws AppException 上传请求、存储、完整性校验或数据库持久化失败
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadResponse upload(
            Integer bidRecordId,
            Long bidderAccountId,
            List<UploadItem> items) {
        validateRequest(bidRecordId, bidderAccountId, items);

        List<String> processedPaths = new ArrayList<>();
        List<UploadResponse.UploadedFile> responseItems = new ArrayList<>();
        try {
            for (int index = 0; index < items.size(); index++) {
                UploadItem item = items.get(index);
                UploadFileContext context = new UploadFileContext(
                        bidRecordId,
                        bidderAccountId,
                        UUID.randomUUID().toString());
                VerifiedStoredFileMetadata verifiedMetadata =
                        uploadItemProcessor.process(context, item);
                processedPaths.add(verifiedMetadata.relativePath());
                CredentialFile file = toCredentialFile(
                        context,
                        index,
                        item,
                        verifiedMetadata);
                credentialFileMapper.insert(file);
                responseItems.add(toResponseItem(
                        file.getFileId(),
                        index,
                        item,
                        verifiedMetadata));
            }
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

        return new UploadResponse(responseItems);
    }

    /**
     * 删除指定投标记录下的一个资信文件及其存储对象。
     *
     * <p>同时删除 original 与已生成的 converted 对象，并同步刷新
     * {@code bid_record.credentials} 投影。</p>
     *
     * @param bidRecordId 文件所属的投标记录 ID
     * @param fileId 资信文件 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer bidRecordId, Long fileId) {
        if (bidRecordId == null || bidRecordId <= 0 || fileId == null || fileId <= 0) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                    "credential-file",
                    "DELETE",
                    "VALIDATE_REQUEST",
                    "资信文件删除参数不正确");
        }
        CredentialFile file = credentialFileMapper.selectById(fileId);
        if (file == null || !Objects.equals(file.getBidRecordId(), bidRecordId)) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_NOT_FOUND,
                    "credential-file",
                    "DELETE",
                    "LOAD_FILE",
                    "资信文件不存在: " + fileId);
        }
        deleteStoredObject(file.getOriginalFilePath(), fileId);
        if (file.getMarkdownFilePath() != null
                && !file.getMarkdownFilePath().isBlank()) {
            deleteStoredObject(file.getMarkdownFilePath(), fileId);
        }
        if (credentialFileMapper.deleteById(fileId) != 1) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_PERSISTENCE_FAILED,
                    "credential-file",
                    "DELETE",
                    "DELETE_FILE_RECORD",
                    "资信文件记录删除失败");
        }
        fileConvertService.syncBidRecordCredentials(bidRecordId);
    }

    private void deleteStoredObject(String relativePath, Long fileId) {
        try {
            fileStorage.delete(relativePath);
        } catch (IOException | RuntimeException failure) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_STORAGE_FAILED,
                    "credential-file",
                    "DELETE",
                    "DELETE_OBJECT",
                    "资信文件存储对象删除失败: " + fileId,
                    failure);
        }
    }

    private void validateRequest(
            Integer bidRecordId,
            Long bidderAccountId,
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

    /** 将已经验证的存储结果转换为对外响应条目。 */
    private UploadResponse.UploadedFile toResponseItem(
            Long fileId,
            int fileOrder,
            UploadItem item,
            VerifiedStoredFileMetadata verifiedMetadata) {
        String contentType = verifiedMetadata.contentType() == null
                ? item.contentType()
                : verifiedMetadata.contentType();
        return new UploadResponse.UploadedFile(
                fileId,
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
            UploadFileContext context,
            int fileOrder,
            UploadItem item,
            VerifiedStoredFileMetadata verifiedMetadata) {
        CredentialFile file = new CredentialFile();
        // file_uuid 为 V21 遗留列；新链路每文件一个文件单元 UUID，
        // 直接复用该列保持表结构不变，不再表示多文件批次。
        file.setFileUuid(context.fileUuid());
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
