package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
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
import io.gitee.lujianshi.bidwinningbackend.credentialfile.model.PreparedCredentialDocument;
import io.gitee.lujianshi.bidwinningbackend.storage.object.ObjectStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordService;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordVectorIndexService;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import io.gitee.lujianshi.bidwinningbackend.storage.vector.CredentialFileVectorStorage;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** 按 bid_record 编排资信文件转换，以文件为最小状态单元。 */
@Service
public class FileConvertService {

    private static final Logger log = LoggerFactory.getLogger(
            FileConvertService.class);
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
    private CredentialFileVectorStorage credentialVectorStorage;

    @Resource
    private ObjectStorage fileStorage;

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
    private BidRecordVectorIndexService bidRecordVectorIndexService;

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
            // credentials 是 credential_file 的派生投影。即使当前已经没有有效文件，
            // 也要重算为空，才能让该入口修复历史删除或后置同步失败留下的旧路径。
            syncBidRecordCredentialsSafely(bidRecordId, null, "EMPTY");
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
     * 重启治理与人工对账共用的单文件收敛入口。
     *
     * <p>稳定终态会校验 DB chunk_count 与 Chroma 实际数量；不一致、处理中断或历史
     * FAILED 都从已校验的 original/Markdown 重新执行替换写入。</p>
     */
    public void reconcileActiveFile(Long fileId) {
        CredentialFile current = fileId == null
                ? null
                : fileMapper.selectByIdIncludingDeleted(fileId);
        if (current == null || current.getDeletedAt() != null
                || current.getDeletionStatus() != null) {
            return;
        }
        if (!"STORED".equals(current.getOriginalStorageStatus())) {
            return;
        }

        boolean interrupted = current.getConvertStatus() == ConvertStatus.CONVERTING
                || "VECTORIZING".equals(current.getIndexStatus());
        if (interrupted) {
            RuntimeException cleanupFailure = null;
            try {
                credentialVectorStorage.deleteDocument(current.getFileUuid());
                cleanupReservedMarkdownForRecovery(current);
            } catch (RuntimeException failure) {
                cleanupFailure = failure;
            }
            fileMapper.markInterruptedProcessingFailed(
                    current.getFileId(), "后端进程重启，自动恢复文件处理");
            if (cleanupFailure != null) {
                throw cleanupFailure;
            }
            current = fileMapper.selectByIdIncludingDeleted(fileId);
        }

        if (current == null || current.getDeletedAt() != null
                || current.getDeletionStatus() != null) {
            return;
        }
        if (current.getConvertStatus() == ConvertStatus.CONVERTED) {
            int actualCount = credentialVectorStorage.getVectorStatus(
                    current.getFileUuid());
            Integer expectedCount = current.getChunkCount();
            if ("VECTORIZED".equals(current.getIndexStatus())
                    && expectedCount != null && expectedCount > 0
                    && actualCount == expectedCount) {
                return;
            }
            credentialVectorStorage.deleteDocument(current.getFileUuid());
            if (fileMapper.markIndexInconsistent(
                    current.getFileId(),
                    "数据库与 Chroma chunk 数不一致，自动重建") != 1) {
                throw persistenceFailure(
                        "MARK_INDEX_INCONSISTENT",
                        "资信文件无法进入索引重建状态: " + fileId,
                        null);
            }
            vectorizeConvertedFile(
                    fileMapper.selectByIdIncludingDeleted(fileId));
            return;
        }

        // 历史转换失败可能已预留或写入 converted key；重转前幂等清理。
        cleanupReservedMarkdownForRecovery(current);
        convertFile(current);
    }

    private void cleanupReservedMarkdownForRecovery(CredentialFile file) {
        String path = file == null ? null : file.getMarkdownFilePath();
        if (path == null || path.isBlank()) {
            return;
        }
        int references = fileMapper.countActiveByMarkdownFilePathExcluding(
                path, file.getFileId());
        if (references > 0) {
            return;
        }
        try {
            fileStorage.delete(path);
        } catch (IOException failure) {
            throw storageFailure(
                    "RECOVER_DELETE_MARKDOWN",
                    "恢复处理中断时删除资信 Markdown 失败: "
                            + file.getFileId(),
                    failure);
        }
    }

    /**
     * 抢占单个文件，并由 Java 完成该文件的转换与状态更新。
     *
     * <p>上传链路与风险分析共用；转换失败只在该文件上留痕（FAILED + CONVERT），
     * 不向上抛错，由调用方按最新状态决定后续。操作历史与
     * {@code bid_record.credentials} 是转换完成后的派生写入：它们失败时必须留痕，
     * 但不能把已校验并落库的 Markdown 转换结果误写成 FAILED。</p>
     */
    public void convertFile(CredentialFile file) {
        int claimed = fileMapper.markConverting(file.getFileId());
        if (claimed != 1) {
            // 没有获得文件处理权不代表转换失败，由后续状态聚合决定整体结果。
            return;
        }
        publishStatus(file);

        // 重转开始即删除旧版本向量并把数据库索引状态重置；转换失败时不会继续
        // 检索上一版正文。删除必须读后验证，失败则本次转换立即收敛为 FAILED。
        try {
            credentialVectorStorage.deleteDocument(file.getFileUuid());
        } catch (RuntimeException failure) {
            markConversionFailed(file, failure);
            return;
        }

        ConvertedMarkdown convertedMarkdown;
        try {
            convertedMarkdown = convertAndStore(file);
        } catch (RuntimeException failure) {
            if (hasDeletionStarted(file.getFileId())) {
                return;
            }
            markConversionFailed(file, failure);
            return;
        }

        try {
            int rows = fileMapper.markConverted(
                    file.getFileId(),
                    convertedMarkdown.markdownPath(),
                    convertedMarkdown.sha256(),
                    LocalDateTime.now());
            if (rows != 1) {
                RuntimeException failure = persistenceFailure(
                        "MARK_FILE_CONVERTED",
                        "资信文件完成状态更新失败: " + file.getFileId(),
                        null);
                cleanupUncommittedConvertedMarkdown(
                        file, convertedMarkdown, failure);
                if (hasDeletionStarted(file.getFileId())) {
                    return;
                }
                markConversionFailed(file, failure);
                return;
            }
        } catch (RuntimeException failure) {
            cleanupUncommittedConvertedMarkdown(
                    file, convertedMarkdown, failure);
            if (hasDeletionStarted(file.getFileId())) {
                return;
            }
            markConversionFailed(file, failure);
            return;
        }

        CredentialFile converted = fileMapper.selectById(file.getFileId());
        publishStatus(file);
        recordConversionHistory(file, converted, null);
        syncBidRecordCredentialsSafely(
                file.getBidRecordId(), file.getFileId(), "CONVERTED");
        vectorizeConvertedFile(converted);
    }

    /** 将真正的 Markdown 转换失败收敛到文件状态；后置写入失败不经过本方法。 */
    private void markConversionFailed(
            CredentialFile file,
            RuntimeException failure) {
        String message = toErrorMessage(failure);
        int rows = fileMapper.markFailed(
                file.getFileId(), message, "CONVERT");
        if (rows != 1) {
            if (hasDeletionStarted(file.getFileId())) {
                return;
            }
            throw persistenceFailure(
                    "MARK_FILE_FAILED",
                    "资信文件失败状态更新失败: " + file.getFileId(),
                    failure);
        }
        CredentialFile failed = fileMapper.selectById(file.getFileId());
        publishStatus(file);
        recordConversionHistory(file, failed, message);
        // 重试已转换文件失败时，立即从 bid_record.credentials 投影中移除
        // 失效路径；对象和文件状态仍以 credential_file 为事实源。
        syncBidRecordCredentialsSafely(
                file.getBidRecordId(), file.getFileId(), "FAILED");
    }

    /**
     * 操作历史是审计副作用，不得反向覆盖已完成的文件事实状态。
     */
    private void recordConversionHistory(
            CredentialFile before,
            CredentialFile after,
            String remark) {
        try {
            operationHistoryService.recordCredentialFile(
                    before.getFileId(),
                    OperationType.UPDATE,
                    before,
                    after,
                    remark);
        } catch (RuntimeException historyFailure) {
            log.error(
                    "资信文件 .md 状态已落库，但操作历史写入失败: fileId={}",
                    before.getFileId(),
                    historyFailure);
        }
    }

    /**
     * {@code bid_record.credentials} 是由 credential_file 重算出的投影；同步失败
     * 不能把已完成的 Markdown 转换误判为失败。
     */
    private void syncBidRecordCredentialsSafely(
            Integer bidRecordId,
            Long fileId,
            String convertStatus) {
        try {
            if (self == null) {
                syncBidRecordCredentials(bidRecordId);
            } else {
                self.syncBidRecordCredentials(bidRecordId);
            }
            bidRecordVectorIndexService.reindex(bidRecordId);
        } catch (RuntimeException projectionFailure) {
            log.error(
                    "资信文件 .md 状态已落库，但 credentials 投影或投标记录索引刷新失败: "
                            + "fileId={}, bidRecordId={}, convertStatus={}",
                    fileId,
                    bidRecordId,
                    convertStatus,
                    projectionFailure);
        }
    }

    /**
     * 删除完成后刷新由文件行重算的投标记录投影。
     *
     * <p>文件软删、对象删除和删除历史已经是事实状态；投影刷新失败只能记录并等待后续
     * 重算，不能让调用方把一次已经完成的文件删除当成失败，也不能阻止父投标记录清理。</p>
     */
    void syncBidRecordCredentialsAfterDeletionSafely(Integer bidRecordId, Long fileId) {
        syncBidRecordCredentialsSafely(bidRecordId, fileId, "DELETED");
    }

    /**
     * 上传完成后的后台转换入口；不阻塞原始文件上传请求。
     *
     * <p>{@link CurrentAccountContext} 基于 ThreadLocal，不能自然跨线程传递。上传线程在
     * 调度前捕获当前操作人，此处仅在本后台任务期间恢复它，保证操作历史仍归属实际上传人。</p>
     */
    @Async("fileProcessingExecutor")
    public void convertFileAsync(
            Long fileId,
            CurrentAccountContext.CurrentAccount operator) {
        CurrentAccountContext.CurrentAccount previous = CurrentAccountContext.get();
        try {
            if (operator == null) {
                CurrentAccountContext.clear();
            } else {
                CurrentAccountContext.set(operator);
            }
            CredentialFile latest = fileMapper.selectById(fileId);
            if (latest != null) {
                convertFile(latest);
            }
        } finally {
            if (previous == null) {
                CurrentAccountContext.clear();
            } else {
                CurrentAccountContext.set(previous);
            }
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
                        latest.getOriginalStorageStatus(),
                        latest.getConvertStatus() == null
                                ? null
                                : latest.getConvertStatus().name(),
                        latest.getIndexStatus());
    }

    /**
     * 将已持久化并回读校验过的 Markdown 写入独立资信 collection。
     *
     * <p>向量化失败只推进 {@code index_status=FAILED}，不会覆盖已经完成的转换事实；
     * 任一失败路径都会再次要求 vector-service 幂等删除 fileUuid 对应 chunks，避免失败状态
     * 仍留下可检索的资信内容。</p>
     */
    private void vectorizeConvertedFile(CredentialFile converted) {
        if (converted == null || converted.getFileId() == null) {
            return;
        }
        if (fileMapper.markVectorizing(converted.getFileId()) != 1) {
            return;
        }
        publishStatus(converted);
        CredentialFile beforeIndex = fileMapper.selectById(converted.getFileId());

        try {
            PreparedCredentialDocument prepared = prepareVectorDocument(converted);
            int chunkCount = credentialVectorStorage.replaceDocument(prepared);
            if (fileMapper.markVectorized(
                    converted.getFileId(),
                    chunkCount,
                    LocalDateTime.now()) != 1) {
                credentialVectorStorage.deleteDocument(converted.getFileUuid());
                throw persistenceFailure(
                        "MARK_FILE_VECTORIZED",
                        "资信文件向量状态更新失败: " + converted.getFileId(),
                        null);
            }
            CredentialFile vectorized = fileMapper.selectById(converted.getFileId());
            publishStatus(vectorized);
            recordConversionHistory(beforeIndex, vectorized, null);
        } catch (RuntimeException failure) {
            try {
                credentialVectorStorage.deleteDocument(converted.getFileUuid());
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            if (hasDeletionStarted(converted.getFileId())) {
                return;
            }
            String message = toErrorMessage(failure);
            int rows = fileMapper.markVectorizeFailed(
                    converted.getFileId(), message);
            if (rows != 1) {
                throw persistenceFailure(
                        "MARK_FILE_VECTORIZE_FAILED",
                        "资信文件向量失败状态更新失败: "
                                + converted.getFileId(),
                        failure);
            }
            CredentialFile failed = fileMapper.selectById(converted.getFileId());
            publishStatus(failed);
            recordConversionHistory(beforeIndex, failed, message);
        }
    }

    /** 从最终 MinIO/本地对象回读 Markdown，校验 SHA 后构造向量化请求。 */
    private PreparedCredentialDocument prepareVectorDocument(
            CredentialFile file) {
        byte[] markdown;
        try (InputStream input = fileStorage.open(file.getMarkdownFilePath())) {
            markdown = input.readAllBytes();
        } catch (IOException failure) {
            throw storageFailure(
                    "READ_MARKDOWN_FOR_VECTORIZE",
                    "读取待向量化资信 Markdown 失败: "
                            + file.getOriginalFilename(),
                    failure);
        }

        String actualSha256 = calculateSha256(markdown);
        if (file.getConvertedSha256() == null
                || !file.getConvertedSha256().equalsIgnoreCase(actualSha256)) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INTEGRITY_CHECK_FAILED,
                    "credential-file",
                    "VECTORIZE",
                    "VERIFY_MARKDOWN",
                    "待向量化资信 Markdown 完整性校验失败");
        }
        BidRecord record = bidRecordService.getById(file.getBidRecordId());
        if (record == null) {
            throw conversionDataInconsistent("资信文件缺少有效投标记录归属");
        }
        return new PreparedCredentialDocument(
                file.getFileUuid(),
                new String(markdown, StandardCharsets.UTF_8),
                file.getFileId(),
                file.getBidRecordId(),
                file.getOriginalFilename(),
                file.getOriginalFilePath(),
                file.getMarkdownFilePath(),
                file.getOriginalSha256(),
                file.getConvertedSha256());
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
        if (fileMapper.reserveMarkdownPath(
                file.getFileId(), markdownPath) != 1) {
            throw persistenceFailure(
                    "RESERVE_MARKDOWN_PATH",
                    "资信文件 Markdown 路径预留失败: " + file.getFileId(),
                    null);
        }
        return storeAndVerifyMarkdown(file, markdownPath, response);
    }

    /**
     * Markdown 已写入存储但对应的状态行未成功提交时立即补偿删除。
     *
     * <p>新 key 含文件 UUID；历史同 key 数据仍通过引用计数保护，不能因为当前记录的失败
     * 覆盖或删除另一条 active 记录的对象。</p>
     */
    private void cleanupUncommittedConvertedMarkdown(
            CredentialFile file,
            ConvertedMarkdown converted,
            RuntimeException originalFailure) {
        if (converted == null || converted.markdownPath() == null
                || converted.markdownPath().isBlank()) {
            return;
        }
        int references = fileMapper.countActiveByMarkdownFilePathExcluding(
                converted.markdownPath(), file.getFileId());
        if (references > 0) {
            log.warn(
                    "保留仍被其他 active 资信文件引用的未提交 Markdown: fileId={}, path={}, references={}",
                    file.getFileId(),
                    converted.markdownPath(),
                    references);
            return;
        }
        try {
            fileStorage.delete(converted.markdownPath());
        } catch (IOException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            log.error(
                    "资信文件 Markdown 状态回填失败后的补偿删除失败: fileId={}, path={}",
                    file.getFileId(),
                    converted.markdownPath(),
                    cleanupFailure);
        }
    }

    /** 删除已经开始后，后台转换不得继续写任何文件事实状态或派生对象。 */
    private boolean hasDeletionStarted(Long fileId) {
        CredentialFile latest = fileMapper.selectByIdIncludingDeleted(fileId);
        return latest != null && latest.getDeletionStatus() != null;
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

        ObjectStorage.StoredFile storedFile = null;
        try {
            storedFile = fileStorage.store(new ObjectStorage.StoreRequest(
                    markdownPath,
                    MARKDOWN_CONTENT_TYPE,
                    markdown.length,
                    metadata,
                    new ByteArrayInputStream(markdown)));
            validateStoredMarkdown(
                    storedFile, markdownPath, markdown.length, metadata);
            try (InputStream storedInput = fileStorage.open(
                    new ObjectStorage.OpenRequest(
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
            ObjectStorage.StoredFile storedFile,
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
        BidRecord lockedRecord = bidRecordMapper.selectByIdForUpdate(bidRecordId);
        if (lockedRecord == null) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_NOT_FOUND,
                    "credential-file",
                    "CONVERT",
                    "SYNC_CREDENTIALS",
                    "投标记录不存在: " + bidRecordId);
        }
        if (lockedRecord.getDeletionStatus() != null) {
            // 父记录删除期间，子文件续删不得重新创建 credentials 投影或私有向量。
            return;
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
                    file.getIndexStatus(),
                    file.getChunkCount(),
                    message));
        }
        return items;
    }
}
