package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatchContext;

import java.util.Map;

/**
 * 资信文件随存储对象持久化的 metadata 契约。
 *
 * <p>该类负责业务 metadata 的名称和构造；通用 {@link FileStorage} 只负责透传和回读，
 * 不解释字段含义。</p>
 *
 * @since 1.0
 */
public final class StoredFileMetadata {

    public static final String BID_RECORD_ID = "bid-record-id";

    public static final String UPLOAD_BATCH_ID = "upload-batch-id";

    public static final String SHA256 = "sha256";

    private StoredFileMetadata() {
    }

    /**
     * 创建原始上传文件需要保存的 metadata。
     *
     * @param context 有效上传批次上下文
     * @param sha256 上传前计算的 SHA-256
     * @return 不可变 metadata
     */
    public static Map<String, String> forUpload(
            UploadBatchContext context,
            String sha256) {
        if (context == null) {
            throw new IllegalArgumentException("上传批次上下文不能为空");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 校验和格式不正确");
        }
        return Map.of(
                BID_RECORD_ID, context.bidRecordId().toString(),
                UPLOAD_BATCH_ID, context.uploadBatchId(),
                SHA256, sha256);
    }

    /**
     * 读取必需 metadata，不存在或为空时拒绝继续处理。
     *
     * @param metadata 存储端回读的 metadata
     * @param key metadata 字段名
     * @return 非空字段值
     */
    public static String require(
            Map<String, String> metadata,
            String key) {
        if (metadata == null) {
            throw new IllegalArgumentException("文件 metadata 不能为空");
        }
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("文件 metadata 缺少字段: " + key);
        }
        return value;
    }
}
