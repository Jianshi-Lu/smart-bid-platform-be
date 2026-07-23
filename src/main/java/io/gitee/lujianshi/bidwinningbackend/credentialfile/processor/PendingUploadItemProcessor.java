package io.gitee.lujianshi.bidwinningbackend.credentialfile.processor;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.PendingUploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatchContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity.StoredFileIntegrityVerifier;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity.VerifiedStoredFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoredFileMetadata;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 单个待上传资信文件的处理器。
 *
 * <p>依次执行上传前校验和计算、持久化存储和存储正文回读校验，将
 * {@link PendingUploadItem} 转换为 {@link VerifiedStoredFile}。当前文件在持久化后发生
 * 任何失败时，由本处理器负责补偿删除。</p>
 *
 * @since 1.0
 */
@Service
public class PendingUploadItemProcessor {

    @Resource
    private FileStorage fileStorage;

    @Resource(name = "sha256FileChecksumCalculator")
    private FileChecksumCalculator fileChecksumCalculator;

    @Resource
    private StoredFileIntegrityVerifier storedFileIntegrityVerifier;

    /**
     * 完成单个待上传文件从请求内容到已验证存储结果的状态转换。
     *
     * @param context 已解析的上传批次上下文
     * @param item 尚未持久化的上传文件
     * @return 已保存并通过正文回读校验的文件
     * @throws IOException 文件内容读取、存储或回读失败
     * @throws RuntimeException 请求边界或文件完整性校验失败
     */
    public VerifiedStoredFile process(
            UploadBatchContext context,
            PendingUploadItem item) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("上传批次上下文不能为空");
        }
        if (item == null) {
            throw new IllegalArgumentException("待上传文件不能为空");
        }

        String sha256 = calculateChecksum(item);
        StoragePath.OriginalFilePath storagePath =
                StoragePath.createOriginalFilePath(
                        context.uploadBatchId(), item.originalFilename());
        FileStorage.StoredFile storedFile = null;
        try {
            try (InputStream input = item.content().openStream()) {
                storedFile = fileStorage.store(
                        new FileStorage.StoreRequest(
                                storagePath.relativePath(),
                                item.contentType(),
                                item.fileSize(),
                                StoredFileMetadata.forUpload(context, sha256),
                                input));
            }
            if (storedFile == null) {
                throw new IOException("文件存储未返回结果");
            }
            return storedFileIntegrityVerifier.verify(
                    storedFile,
                    storagePath.safeOriginalFilename(),
                    sha256);
        } catch (IOException | RuntimeException failure) {
            if (storedFile != null) {
                cleanupCurrentFile(storedFile.relativePath(), failure);
            }
            throw failure;
        }
    }

    /** 打开第一条内容流并计算上传前 SHA-256。 */
    private String calculateChecksum(PendingUploadItem item) throws IOException {
        try (InputStream input = item.content().openStream()) {
            return fileChecksumCalculator.calculate(input);
        }
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
