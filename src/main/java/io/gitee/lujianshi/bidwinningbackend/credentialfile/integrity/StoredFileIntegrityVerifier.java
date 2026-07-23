package io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoredFileMetadata;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 已存储文件正文完整性校验器。
 *
 * <p>通过存储端口重新打开文件并流式计算 SHA-256；MinIO 模式下使用 statObject
 * 回读的 ETag 执行条件读取，并将实际校验和与对象 metadata 中的校验和比较。</p>
 *
 * @since 1.0
 */
@Service
public class StoredFileIntegrityVerifier {

    @Resource
    private FileStorage fileStorage;

    @Resource(name = "sha256FileChecksumCalculator")
    private FileChecksumCalculator fileChecksumCalculator;

    /**
     * 重新读取并校验已存储文件正文。
     *
     * @param storedFile 存储端确认的文件信息
     * @param safeOriginalFilename 服务端规范化后的安全原始文件名
     * @param uploadedSha256 写入存储前对请求内容计算的 SHA-256
     * @return 已通过正文完整性校验的存储文件
     * @throws IOException 文件无法重新读取或校验和计算失败
     * @throws FileIntegrityException metadata 或文件正文校验和不一致
     */
    public VerifiedStoredFile verify(
            FileStorage.StoredFile storedFile,
            String safeOriginalFilename,
            String uploadedSha256) throws IOException {
        validateInput(storedFile, uploadedSha256);

        String expectedSha256 = requireMetadata(
                storedFile,
                StoredFileMetadata.SHA256);
        validateBusinessMetadata(storedFile);
        if (!uploadedSha256.equals(expectedSha256)) {
            throw new FileIntegrityException(
                    "存储对象 SHA-256 metadata 与上传校验和不一致: "
                            + storedFile.relativePath());
        }

        String actualSha256;
        try (InputStream input = fileStorage.open(
                new FileStorage.OpenRequest(
                        storedFile.relativePath(), storedFile.etag()))) {
            actualSha256 = fileChecksumCalculator.calculate(input);
        }
        if (!expectedSha256.equals(actualSha256)) {
            throw new FileIntegrityException(
                    "存储对象内容完整性校验失败: " + storedFile.relativePath());
        }
        return VerifiedStoredFile.from(
                storedFile,
                safeOriginalFilename,
                actualSha256);
    }

    /** 校验完整性校验入口参数。 */
    private void validateInput(
            FileStorage.StoredFile storedFile,
            String uploadedSha256) {
        if (storedFile == null) {
            throw new IllegalArgumentException("已存储文件不能为空");
        }
        if (uploadedSha256 == null || !uploadedSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 校验和格式不正确");
        }
    }

    /** 读取完整性校验依赖的必需 metadata。 */
    private String requireMetadata(
            FileStorage.StoredFile storedFile,
            String key) {
        try {
            return StoredFileMetadata.require(storedFile.metadata(), key);
        } catch (IllegalArgumentException e) {
            throw new FileIntegrityException(
                    "存储对象 metadata 不完整: "
                            + storedFile.relativePath());
        }
    }

    /** 校验后续可信结果和数据库记录依赖的业务 metadata。 */
    private void validateBusinessMetadata(
            FileStorage.StoredFile storedFile) {
        String bidRecordId = requireMetadata(
                storedFile, StoredFileMetadata.BID_RECORD_ID);
        String uploadBatchId = requireMetadata(
                storedFile, StoredFileMetadata.UPLOAD_BATCH_ID);
        try {
            if (Integer.parseInt(bidRecordId) <= 0) {
                throw new IllegalArgumentException();
            }
            UUID.fromString(uploadBatchId);
        } catch (IllegalArgumentException e) {
            throw new FileIntegrityException(
                    "存储对象 metadata 格式不正确: "
                            + storedFile.relativePath());
        }
    }
}
