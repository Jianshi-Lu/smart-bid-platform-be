package io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage;

import java.util.UUID;

/**
 * 资信文件存储 Key 生成与校验工具。
 *
 * <p>在文件进入具体存储实现前，统一生成与存储介质无关的标准路径；本地文件系统或 MinIO
 * 再分别解释该路径并完成实际读写。本类只负责路径命名与校验，不执行文件存储。</p>
 */
final class CredentialFileStorageKey {

    private CredentialFileStorageKey() {
    }

    /**
     * 为原始文件生成批次内唯一的存储 Key。
     *
     * @param uploadBatchId 上传批次 UUID
     * @param originalFilename 客户端提供的原始文件名
     * @return 安全原始文件名及存储 Key
     */
    static OriginalFileKey createOriginalFileKey(String uploadBatchId, String originalFilename) {
        String normalizedBatchId;
        try {
            normalizedBatchId = UUID.fromString(uploadBatchId).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("上传批次号格式不正确", e);
        }

        String safeOriginalFilename = normalizeFilename(originalFilename);
        String storedFilename = UUID.randomUUID() + "_" + safeOriginalFilename;
        return new OriginalFileKey(
                safeOriginalFilename,
                normalizedBatchId + "/original/" + storedFilename);
    }

    /**
     * 校验并统一存储 Key 中的路径分隔符。
     *
     * @param relativePath 存储相对路径或 MinIO object key
     * @return 使用正斜杠的安全存储 Key
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

    /** 原始文件安全命名结果。 */
    record OriginalFileKey(String safeOriginalFilename, String relativePath) {
    }
}
