package io.gitee.lujianshi.bidwinningbackend.credentialfile.processor;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadFileContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 单个上传资信文件的处理器。
 *
 * <p>依次执行上传前校验和计算、持久化存储和存储正文回读校验，将
 * {@link UploadItem} 转换为 {@link VerifiedStoredFileMetadata}。当前文件在持久化后发生
 * 任何失败时，由本处理器负责补偿删除。</p>
 *
 * @since 1.0
 */
@Service
public class UploadItemProcessor {

    private static final String BID_RECORD_ID_METADATA = "bid-record-id";
    private static final String BIDDER_ACCOUNT_ID_METADATA = "bidder-account-id";
    private static final String FILE_UUID_METADATA = "file-uuid";
    private static final String SHA256_METADATA = "sha256";

    @Resource
    private FileStorage fileStorage;

    @Resource(name = "sha256FileChecksumCalculator")
    private FileChecksumCalculator fileChecksumCalculator;

    /**
     * 完成单个上传文件从请求内容到已验证存储结果的状态转换。
     *
     * @param context 已解析的文件单元上传上下文
     * @param item 尚未持久化的上传文件
     * @return 已保存并通过正文回读校验的文件元数据
     * @throws IOException 文件内容读取、存储或回读失败
     * @throws RuntimeException 请求边界或文件完整性校验失败
     */
    public VerifiedStoredFileMetadata process(
            UploadFileContext context,
            UploadItem item) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("上传文件上下文不能为空");
        }
        if (item == null) {
            throw new IllegalArgumentException("待上传文件不能为空");
        }

        // 首次计算文件checksum
        String expectedSha256;
        try (InputStream inputStream = item.content().openStream()) {
            expectedSha256 = fileChecksumCalculator.calculate(inputStream);
        }
        StoragePath.OriginalFilePath storagePath;
        try {
            storagePath = StoragePath.createOriginalFilePath(
                    context.username(),
                    context.fileUuid(),
                    item.originalFilename());
        } catch (IllegalArgumentException failure) {
            throw new AppException(
                    AppErrorCode.CREDENTIAL_FILE_INVALID_PATH,
                    "credential-file",
                    "UPLOAD",
                    "BUILD_STORAGE_PATH",
                    "资信文件存储路径不合法",
                    failure);
        }
        FileStorage.StoredFile storedFile = null;
        try {
            // 开启文件流存储文件，并写入首次计算的checksum
            try (InputStream inputStream = item.content().openStream()) {
                storedFile = fileStorage.store(
                        new FileStorage.StoreRequest(
                                storagePath.relativePath(),
                                item.contentType(),
                                item.fileSize(),
                                createMetadata(context, expectedSha256),
                                inputStream));
            }
            if (storedFile == null) {
                throw new IOException("文件存储未返回结果");
            }

            String actualSha256;
            // 从MinIO中打开文件流
            try (InputStream inputStream = fileStorage.open(
                    new FileStorage.OpenRequest(
                            storedFile.relativePath(), storedFile.etag()))) {
                // 再次计算checksum并与首次对比，校验存储完整性
                actualSha256 = fileChecksumCalculator.calculate(inputStream);
            }
            if (!expectedSha256.equals(actualSha256)) {
                throw new AppException(
                        AppErrorCode.CREDENTIAL_FILE_INTEGRITY_CHECK_FAILED,
                        "credential-file",
                        "UPLOAD",
                        "VERIFY_STORED_FILE",
                        "文件完整性校验失败");
            }
            return VerifiedStoredFileMetadata.extractMetadata(
                    storedFile,
                    storagePath.safeOriginalFilename(),
                    expectedSha256);
        } catch (IOException | RuntimeException failure) {
            if (storedFile != null) {
                cleanupCurrentFile(storedFile.relativePath(), failure);
            }
            throw failure;
        }
    }

    /** 构造由上传流程定义、存储端仅负责透传的对象 metadata。 */
    private Map<String, String> createMetadata(
            UploadFileContext context,
            String sha256) {
        return Map.of(
                BID_RECORD_ID_METADATA, context.bidRecordId().toString(),
                BIDDER_ACCOUNT_ID_METADATA, context.bidderAccountId().toString(),
                FILE_UUID_METADATA, context.fileUuid(),
                SHA256_METADATA, sha256);
    }

    /** 尽力删除当前文件，并将清理失败作为原始异常的 suppressed exception。 */
    private void cleanupCurrentFile(
            String relativePath,
            Throwable originalFailure) {
        try {
            fileStorage.delete(relativePath);
        } catch (IOException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }
}
