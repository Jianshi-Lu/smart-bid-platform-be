package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.ToMarkdownClient;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.BidRecordCredentialConversionStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.service.BidRecordService;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** 按 bid_record 编排资信文件转换，以文件为最小状态单元。 */
@Service
public class FileConvertService {

    private static final String MARKDOWN_CONTENT_TYPE =
            "text/markdown; charset=utf-8";
    private static final String BID_RECORD_ID_METADATA = "bid-record-id";
    private static final String FILE_UUID_METADATA = "file-uuid";
    private static final String SOURCE_FILE_ID_METADATA = "source-file-id";
    private static final String SOURCE_SHA256_METADATA = "source-sha256";
    private static final String MARKDOWN_SHA256_METADATA = "markdown-sha256";

    @Resource
    private CredentialFileMapper fileMapper;

    @Resource
    private ToMarkdownClient toMarkdownClient;

    @Resource
    private FileStorage fileStorage;

    @Resource
    private FileStorageProperties storageProperties;

    @Resource
    private OperationHistoryService operationHistoryService;

    @Resource(name = "sha256FileChecksumCalculator")
    private FileChecksumCalculator fileChecksumCalculator;

    @Resource
    private BidRecordService bidRecordService;

    @Resource
    private BidRecordMapper bidRecordMapper;

    @Resource
    private FileStatusEventPublisher fileStatusEventPublisher;

    /** 通过 Spring 代理开启路径汇总事务，避免同类调用绕过事务注解。 */
    @Resource
    @Lazy
    private FileConvertService self;

    /**
     * 无差别重新处理指定投标记录下的全部资信文件，并返回文件级汇总状态。
     *
     * <p>Java 读取 original 文件、校验其 SHA-256、multipart 调用 Python、保存并回读
     * converted Markdown。重复转换使用同一个 converted 路径并覆盖旧派生对象。</p>
     */
    public FileConvertResponse convertAll(Integer bidRecordId) {
        validateBidRecord(bidRecordId);

        List<CredentialFile> files = fileMapper.selectByBidRecordId(bidRecordId);
        if (files == null || files.isEmpty()) {
            return new FileConvertResponse(
                    bidRecordId,
                    BidRecordCredentialConversionStatus.ALL_CONVERTED,
                    "该投标记录暂无资信文件",
                    0,
                    0,
                    0,
                    0,
                    List.of());
        }

        for (CredentialFile file : files) {
            convertFile(file);
        }

        List<CredentialFile> latestFiles =
                fileMapper.selectByBidRecordId(bidRecordId);
        if (latestFiles == null) {
            latestFiles = List.of();
        }

        int convertedCount = countByStatus(latestFiles, ConvertStatus.CONVERTED);
        int failedCount = countByStatus(latestFiles, ConvertStatus.FAILED);
        int processingCount = latestFiles.size() - convertedCount - failedCount;

        BidRecordCredentialConversionStatus overallStatus;
        String message;
        if (failedCount > 0) {
            overallStatus = BidRecordCredentialConversionStatus.PARTIAL_FAILED;
            message = "部分资信文件转换失败";
        } else if (convertedCount == latestFiles.size()) {
            overallStatus = BidRecordCredentialConversionStatus.ALL_CONVERTED;
            message = "该投标记录的所有资信文件已经完成转换";
        } else {
            overallStatus = BidRecordCredentialConversionStatus.PROCESSING;
            message = "部分资信文件仍在处理中";
        }

        return new FileConvertResponse(
                bidRecordId,
                overallStatus,
                message,
                latestFiles.size(),
                convertedCount,
                processingCount,
                failedCount,
                toFileItems(latestFiles));
    }

    /**
     * 抢占单个文件，并由 Java 完成该文件的转换与状态更新。
     *
     * <p>上传链路与风险分析共用；转换失败只在该文件上留痕（FAILED + CONVERT），
     * 不向上抛错，由调用方按最新状态决定后续。</p>
     */
    public void convertFile(CredentialFile file) {
        int claimed = fileMapper.markConverting(file.getFileId());
        if (claimed != 1) {
            // 没有获得文件处理权不代表转换失败，由后续状态聚合决定整体结果。
            return;
        }

        try {
            ConvertedMarkdown converted = convertAndStore(file);
            int rows = fileMapper.markConverted(
                    file.getFileId(),
                    converted.markdownPath(),
                    converted.sha256(),
                    LocalDateTime.now());
            if (rows != 1) {
                throw persistenceFailure(
                        "MARK_FILE_CONVERTED",
                        "资信文件完成状态更新失败: " + file.getFileId(),
                        null);
            }
            operationHistoryService.recordCredentialFile(
                    file.getFileId(),
                    OperationType.UPDATE,
                    file,
                    fileMapper.selectById(file.getFileId()),
                    null);
            publishStatus(file);
            // 单文件转换成功后立即同步当前 bid_record 下已完成文件的 Markdown 路径。
            self.syncBidRecordCredentials(file.getBidRecordId());
        } catch (RuntimeException failure) {
            String message = toErrorMessage(failure);
            int rows = fileMapper.markFailed(
                    file.getFileId(), message, "CONVERT");
            if (rows != 1) {
                throw persistenceFailure(
                        "MARK_FILE_FAILED",
                        "资信文件失败状态更新失败: " + file.getFileId(),
                        failure);
            }
            operationHistoryService.recordCredentialFile(
                    file.getFileId(),
                    OperationType.UPDATE,
                    file,
                    fileMapper.selectById(file.getFileId()),
                    message);
            publishStatus(file);
            // 重试已转换文件失败时，立即从 bid_record.credentials 投影中移除
            // 失效路径；对象和文件状态仍以 credential_file 为事实源。
            self.syncBidRecordCredentials(file.getBidRecordId());
        }
    }

    /** 上传完成后的后台转换入口；不阻塞原始文件上传请求。 */
    @Async("fileProcessingExecutor")
    public void convertFileAsync(Long fileId) {
        CredentialFile latest = fileMapper.selectById(fileId);
        if (latest != null) {
            convertFile(latest);
        }
    }

    private void publishStatus(CredentialFile file) {
        if (fileStatusEventPublisher == null) return;
        CredentialFile latest = fileMapper.selectById(file.getFileId());
        if (latest == null) return;
                fileStatusEventPublisher.publish(
                        FileStatusEventPublisher.CREDENTIAL_TOPIC_PREFIX
                                + latest.getBidRecordId(),
                        String.valueOf(latest.getFileId()),
                        latest.getConvertStatus() == null
                                ? null
                                : latest.getConvertStatus().name(),
                        null,
                        latest.getOriginalStorageStatus());
    }

    /**
     * 完成一个原文件从存储读取、Python 转换到 Markdown 持久化的闭环。
     *
     * <p>原文件流在 HTTP 请求完成后关闭。Python 返回正文先保存在内存中，以便计算
     * Markdown SHA-256、声明准确 Content-Length，并在存储后回读校验。</p>
     */
    private ConvertedMarkdown convertAndStore(CredentialFile file) {
        validateFileMetadata(file);
        PythonMarkdownResponse response = requestMarkdown(file);
        String markdownPath = createMarkdownPath(
                file.getOriginalFilePath(), response.filename());
        return storeAndVerifyMarkdown(file, markdownPath, response);
    }

    /** 从配置的存储介质读取原文件，并在发送过程中重新计算原文件 SHA-256。 */
    private PythonMarkdownResponse requestMarkdown(CredentialFile file) {
        MessageDigest sourceDigest = createSha256Digest();
        PythonMarkdownResponse response;
        try (InputStream originalInput =
                     fileStorage.open(file.getOriginalFilePath());
             DigestInputStream verifiedInput =
                     new DigestInputStream(originalInput, sourceDigest)) {
            response = toMarkdownClient.convert(
                    verifiedInput,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getFileSize());
        } catch (IOException failure) {
            throw storageFailure(
                    "READ_ORIGINAL",
                    "读取待转换原文件失败: " + file.getOriginalFilename(),
                    failure);
        }

        String actualSha256 =
                HexFormat.of().formatHex(sourceDigest.digest());
        if (!file.getOriginalSha256().equalsIgnoreCase(actualSha256)) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INTEGRITY_CHECK_FAILED,
                    "credential-file",
                    "CONVERT",
                    "VERIFY_ORIGINAL",
                    "待转换原文件完整性校验失败");
        }
        return response;
    }

    /** 上传 Python 返回的 Markdown，并通过存储属性和正文回读校验写入结果。 */
    private ConvertedMarkdown storeAndVerifyMarkdown(
            CredentialFile file,
            String markdownPath,
            PythonMarkdownResponse response) {
        byte[] markdown = response.markdown();
        String markdownSha256 = calculateSha256(markdown);
        Map<String, String> metadata = Map.of(
                BID_RECORD_ID_METADATA, file.getBidRecordId().toString(),
                FILE_UUID_METADATA, file.getFileUuid(),
                SOURCE_FILE_ID_METADATA, file.getFileId().toString(),
                SOURCE_SHA256_METADATA, file.getOriginalSha256(),
                MARKDOWN_SHA256_METADATA, markdownSha256);

        FileStorage.StoredFile storedFile = null;
        try {
            storedFile = fileStorage.store(new FileStorage.StoreRequest(
                    markdownPath,
                    MARKDOWN_CONTENT_TYPE,
                    markdown.length,
                    metadata,
                    new ByteArrayInputStream(markdown)));
            validateStoredMarkdown(
                    storedFile, markdownPath, markdown.length, metadata);
            try (InputStream storedInput = fileStorage.open(
                    new FileStorage.OpenRequest(
                            storedFile.relativePath(), storedFile.etag()))) {
                String actualSha256 =
                        fileChecksumCalculator.calculate(storedInput);
                if (!markdownSha256.equals(actualSha256)) {
                    throw new AppException(
                            AppErrorCode.CREDENTIAL_FILE_INTEGRITY_CHECK_FAILED,
                            "credential-file",
                            "CONVERT",
                            "VERIFY_MARKDOWN",
                            "转换后的 Markdown 完整性校验失败");
                }
            }
            return new ConvertedMarkdown(
                    storedFile.relativePath(), markdownSha256);
        } catch (IOException failure) {
            if (storedFile != null) {
                cleanupConvertedMarkdown(
                        storedFile.relativePath(), failure);
            }
            throw storageFailure(
                    "STORE_MARKDOWN",
                    "保存转换后的 Markdown 失败: "
                            + file.getOriginalFilename(),
                    failure);
        } catch (RuntimeException failure) {
            if (storedFile != null) {
                cleanupConvertedMarkdown(
                        storedFile.relativePath(), failure);
            }
            throw failure;
        }
    }

    /** 校验存储层回报的路径、大小、类型和 metadata 均与本次转换请求一致。 */
    private void validateStoredMarkdown(
            FileStorage.StoredFile storedFile,
            String expectedPath,
            long expectedSize,
            Map<String, String> expectedMetadata) {
        if (storedFile == null) {
            throw storageFailure(
                    "STORE_MARKDOWN", "Markdown 存储未返回结果", null);
        }
        if (!expectedPath.equals(storedFile.relativePath())
                || storedFile.fileSize() != expectedSize
                || storedFile.contentType() == null
                || !storedFile.contentType().toLowerCase()
                .startsWith("text/markdown")
                || !expectedMetadata.equals(storedFile.metadata())) {
            throw storageFailure(
                    "VALIDATE_STORED_MARKDOWN",
                    "Markdown 存储结果与转换结果不一致",
                    null);
        }
    }

    /** 校验单文件转换所依赖的数据库元数据，避免用残缺记录访问外部服务。 */
    private void validateFileMetadata(CredentialFile file) {
        if (file == null
                || file.getFileId() == null
                || file.getBidRecordId() == null
                || file.getFileUuid() == null
                || file.getFileUuid().isBlank()
                || file.getOriginalFilename() == null
                || file.getOriginalFilename().isBlank()
                || file.getOriginalFilePath() == null
                || file.getOriginalFilePath().isBlank()
                || file.getFileSize() == null
                || file.getFileSize() <= 0
                || file.getOriginalSha256() == null
                || !file.getOriginalSha256().matches("(?i)[0-9a-f]{64}")
                || file.getStorageType() == null
                || storageProperties == null
                || storageProperties.getType() == null
                || !storageProperties.getType().name()
                .equals(file.getStorageType())) {
            throw conversionDataInconsistent(
                    "资信文件转换元数据不完整或存储类型与当前配置不一致");
        }
    }

    /** 将 original 路径映射为文件单元内固定的 converted Markdown 路径。 */
    private String createMarkdownPath(
            String originalFilePath,
            String markdownFilename) {
        try {
            return StoragePath.createConvertedMarkdownPath(
                    originalFilePath, markdownFilename);
        } catch (IllegalArgumentException failure) {
            throw conversionFailure(
                    "BUILD_MARKDOWN_PATH",
                    "无法根据原文件生成 Markdown 路径",
                    failure);
        }
    }

    /** 计算内存中 Python 响应正文的 SHA-256。 */
    private String calculateSha256(byte[] content) {
        try {
            return fileChecksumCalculator.calculate(
                    new ByteArrayInputStream(content));
        } catch (IOException failure) {
            throw conversionFailure(
                    "CHECKSUM_MARKDOWN",
                    "无法计算转换后 Markdown 的校验和",
                    failure);
        }
    }

    /** 创建用于边发送边校验原文件的 SHA-256 摘要器。 */
    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(
                    "当前 Java 运行时不支持 SHA-256", failure);
        }
    }

    /** 回读校验失败时删除本次 converted 对象，并保留原始失败。 */
    private void cleanupConvertedMarkdown(
            String markdownPath,
            Throwable originalFailure) {
        try {
            fileStorage.delete(markdownPath);
        } catch (IOException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private String toErrorMessage(Throwable failure) {
        return toErrorMessage(failure == null ? null : failure.getMessage());
    }

    private String toErrorMessage(String errorMessage) {
        String message = errorMessage == null || errorMessage.isBlank()
                ? "资信文件转换失败"
                : errorMessage;
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        return message;
    }

    /**
     * 将当前投标记录下已完成文件的 Markdown object key 列表写回 bid_record。
     *
     * <p>先锁定 bid_record 行，再查询文件路径并整体更新 JSON，避免多个文件并发转换时发生
     * 读改写覆盖。事务只包含数据库查询和更新，不包含 Python 调用。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncBidRecordCredentials(Integer bidRecordId) {
        if (bidRecordMapper.selectByIdForUpdate(bidRecordId) == null) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_NOT_FOUND,
                    "credential-file",
                    "CONVERT",
                    "SYNC_CREDENTIALS",
                    "投标记录不存在: " + bidRecordId);
        }

        List<CredentialFile> files = fileMapper.selectByBidRecordId(bidRecordId);
        if (files == null) {
            files = List.of();
        }

        List<String> markdownPaths = files.stream()
                .filter(file -> file.getConvertStatus() == ConvertStatus.CONVERTED)
                .map(CredentialFile::getMarkdownFilePath)
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .toList();

        BidRecord patch = new BidRecord();
        patch.setBidRecordId(bidRecordId);
        patch.setCredentials(markdownPaths);
        if (bidRecordMapper.updateById(patch) != 1) {
            throw persistenceFailure(
                    "SYNC_CREDENTIALS",
                    "投标记录资信材料路径更新失败",
                    null);
        }
    }

    private void validateBidRecord(Integer bidRecordId) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new AppException(
                    AppErrorCode.COMMON_INVALID_ARGUMENT,
                    "credential-file",
                    "CONVERT",
                    "VALIDATE_BID_RECORD",
                    "投标记录 ID 不正确");
        }
        if (bidRecordService.getById(bidRecordId) == null) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_NOT_FOUND,
                    "credential-file",
                    "CONVERT",
                    "VALIDATE_BID_RECORD",
                    "投标记录不存在");
        }
    }

    /** 创建转换数据不一致异常。 */
    private AppException conversionDataInconsistent(String message) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_CONVERSION_DATA_INCONSISTENT,
                "credential-file",
                "CONVERT",
                "VALIDATE_CONVERSION_DATA",
                message);
    }

    /** 创建转换失败异常。 */
    private AppException conversionFailure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_CONVERSION_FAILED,
                "credential-file",
                "CONVERT",
                stage,
                message,
                cause);
    }

    /** 创建文件持久化失败异常。 */
    private AppException persistenceFailure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_PERSISTENCE_FAILED,
                "credential-file",
                "CONVERT",
                stage,
                message,
                cause);
    }

    /** 创建文件存储或回读失败异常。 */
    private AppException storageFailure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_STORAGE_FAILED,
                "credential-file",
                "CONVERT",
                stage,
                message,
                cause);
    }

    private int countByStatus(
            List<CredentialFile> files,
            ConvertStatus status) {
        return (int) files.stream()
                .filter(file -> file.getConvertStatus() == status)
                .count();
    }

    /** 已通过存储回读校验的 Markdown 转换产物。 */
    private record ConvertedMarkdown(String markdownPath, String sha256) {
    }

    private List<FileConvertItem> toFileItems(List<CredentialFile> files) {
        List<FileConvertItem> items = new ArrayList<>();
        for (CredentialFile file : files) {
            String message = switch (file.getConvertStatus()) {
                case UPLOADING -> "文件仍在上传中";
                case UPLOADED -> "等待转换";
                case CONVERTING -> "正在转换";
                case CONVERTED -> "转换完成";
                case FAILED -> file.getErrorMessage() == null
                        ? "转换失败"
                        : file.getErrorMessage();
            };
            items.add(new FileConvertItem(
                    file.getFileId(),
                    file.getOriginalFilename(),
                    file.getConvertStatus(),
                    message));
        }
        return items;
    }
}
