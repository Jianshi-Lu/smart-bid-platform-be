package io.gitee.lujianshi.bidwinningbackend.credentialfile.storage;

import java.util.UUID;

/**
 * 存储介质无关的资信文件逻辑路径生成与校验工具。
 *
 * <p>本地文件系统将逻辑路径解释为根目录下的相对路径，MinIO 将其解释为 object key。
 * 该类只维护统一命名和安全边界，不执行文件读写。</p>
 */
public final class StoragePath {

    private StoragePath() {
    }

    /**
     * 为原始文件生成以投标记录为顶层归属、以批次为第二层归属的存储路径。
     *
     * <p>MinIO 中的目录是 object key 前缀，本地文件系统则会将同一个逻辑路径解释为
     * 存储根目录下的相对路径。</p>
     *
     * @param bidRecordId 投标记录 ID
     * @param uploadBatchId 上传批次 UUID
     * @param originalFilename 客户端提供的原始文件名
     * @return 安全原始文件名及逻辑存储路径
     */
    public static OriginalFilePath createOriginalFilePath(
            Integer bidRecordId,
            String uploadBatchId,
            String originalFilename) {
        validateBidRecordId(bidRecordId);
        String normalizedBatchId;
        try {
            normalizedBatchId = UUID.fromString(uploadBatchId).toString();
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new IllegalArgumentException("上传批次号格式不正确", e);
        }

        String safeOriginalFilename = normalizeFilename(originalFilename);
        String storedFilename = UUID.randomUUID() + "_" + safeOriginalFilename;
        return new OriginalFilePath(
                safeOriginalFilename,
                "bid-records/" + bidRecordId
                        + "/batches/" + normalizedBatchId
                        + "/original/" + storedFilename);
    }

    /**
     * 生成投标记录级汇总 Markdown 的固定逻辑路径。
     *
     * <p>该路径是派生文件路径，不对应任何 {@code credential_file} 明细记录；每次风险分析
     * 前重新生成并覆盖同一个对象。</p>
     *
     * @param bidRecordId 投标记录 ID
     * @return 汇总 Markdown 的逻辑存储路径
     */
    public static String createMergedMarkdownPath(Integer bidRecordId) {
        validateBidRecordId(bidRecordId);
        return "bid-records/" + bidRecordId + "/merged/credentials.md";
    }

    /**
     * 校验并统一逻辑路径中的分隔符。
     *
     * @param relativePath 本地相对路径或 MinIO object key
     * @return 使用正斜杠的安全逻辑路径
     */
    static String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("文件相对路径不能为空");
        }

        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException("文件相对路径不合法");
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("文件相对路径不合法");
            }
        }
        return normalized;
    }

    /** 去除客户端路径片段和控制字符，得到安全文件名。 */
    private static String normalizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("原始文件名不能为空");
        }

        String unifiedFilename = originalFilename.replace('\\', '/');
        String filename = unifiedFilename.substring(unifiedFilename.lastIndexOf('/') + 1).trim();
        filename = filename.replaceAll("[\\p{Cntrl}]", "_");
        if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
            throw new IllegalArgumentException("原始文件名不合法");
        }
        return filename;
    }

    /** 校验并保持所有存储实现使用一致的投标记录路径边界。 */
    private static void validateBidRecordId(Integer bidRecordId) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new IllegalArgumentException("投标记录 ID 不正确");
        }
    }

    /** 原始文件安全命名和逻辑路径结果。 */
    public record OriginalFilePath(String safeOriginalFilename, String relativePath) {
    }
}
