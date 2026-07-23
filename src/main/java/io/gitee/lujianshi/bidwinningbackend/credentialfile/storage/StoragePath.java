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
     * 为原始文件生成批次内唯一的存储路径。
     *
     * @param uploadBatchId 上传批次 UUID
     * @param originalFilename 客户端提供的原始文件名
     * @return 安全原始文件名及逻辑存储路径
     */
    public static OriginalFilePath createOriginalFilePath(
            String uploadBatchId,
            String originalFilename) {
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
                normalizedBatchId + "/original/" + storedFilename);
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

    /** 原始文件安全命名和逻辑路径结果。 */
    public record OriginalFilePath(String safeOriginalFilename, String relativePath) {
    }
}
