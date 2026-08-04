package io.gitee.lujianshi.bidwinningbackend.knowledgefile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.StoredKnowledgeObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** 复用 FileStorage 完成知识对象写入、回读和 SHA-256 完整性校验。 */
@Service
public class KnowledgeFileObjectService {

    private static final String SHA256_METADATA = "sha256";

    private final FileStorage fileStorage;
    private final FileChecksumCalculator checksumCalculator;

    public KnowledgeFileObjectService(
            FileStorage fileStorage,
            @Qualifier("sha256FileChecksumCalculator")
            FileChecksumCalculator checksumCalculator) {
        this.fileStorage = fileStorage;
        this.checksumCalculator = checksumCalculator;
    }

    /** 保存对象，并从持久化介质重新读取正文计算 SHA-256。 */
    public StoredKnowledgeObject storeAndVerify(
            String objectKey,
            String contentType,
            long contentLength,
            Map<String, String> metadata,
            KnowledgeFileUpload.Content content) throws IOException {
        String expectedSha256;
        try (InputStream input = content.openStream()) {
            expectedSha256 = checksumCalculator.calculate(input);
        }

        Map<String, String> storedMetadata = new LinkedHashMap<>(
                metadata == null ? Map.of() : metadata);
        storedMetadata.put(SHA256_METADATA, expectedSha256);

        FileStorage.StoredFile storedFile = null;
        try {
            try (InputStream input = content.openStream()) {
                storedFile = fileStorage.store(new FileStorage.StoreRequest(
                        objectKey,
                        contentType,
                        contentLength,
                        storedMetadata,
                        input));
            }
            if (storedFile == null || storedFile.fileSize() != contentLength) {
                throw new IOException("知识对象存储结果不完整: " + objectKey);
            }

            String actualSha256;
            try (InputStream input = fileStorage.open(
                    new FileStorage.OpenRequest(
                            storedFile.relativePath(), storedFile.etag()))) {
                actualSha256 = checksumCalculator.calculate(input);
            }
            if (!expectedSha256.equals(actualSha256)) {
                throw new AppException(
                        AppErrorCode.KNOWLEDGE_FILE_INTEGRITY_CHECK_FAILED,
                        "knowledge-file",
                        "STORE",
                        "VERIFY_STORED_OBJECT",
                        "知识文件完整性校验失败");
            }

            return new StoredKnowledgeObject(
                    storedFile.relativePath(),
                    storedFile.fileSize(),
                    storedFile.contentType(),
                    expectedSha256,
                    storedFile.etag());
        } catch (IOException | RuntimeException failure) {
            if (storedFile != null) {
                cleanupCurrentObject(storedFile.relativePath(), failure);
            }
            throw failure;
        }
    }

    public InputStream open(StoredKnowledgeObject object) throws IOException {
        return fileStorage.open(new FileStorage.OpenRequest(
                object.objectKey(), object.etag()));
    }

    /** 文件不存在时由底层存储保持幂等。 */
    public void delete(String objectKey) throws IOException {
        fileStorage.delete(objectKey);
    }

    private void cleanupCurrentObject(
            String objectKey,
            Throwable originalFailure) {
        try {
            fileStorage.delete(objectKey);
        } catch (IOException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }
}
