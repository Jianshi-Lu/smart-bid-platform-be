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
     * 为原始文件生成以投标方用户名为顶层归属、以文件名为目录的存储路径。
     *
     * <p>MinIO 中的目录是 object key 前缀，本地文件系统则会将同一个逻辑路径解释为
     * 存储根目录下的相对路径。</p>
     *
     * @param username 投标方登录用户名（资信文件归属与溯源根）
     * @param fileUuid 文件单元 UUID
     * @param originalFilename 客户端提供的原始文件名
     * @return 安全原始文件名及逻辑存储路径
     */
    public static OriginalFilePath createOriginalFilePath(
            String username,
            String fileUuid,
            String originalFilename) {
        validateUsername(username);
        String normalizedFileUuid;
        try {
            normalizedFileUuid = UUID.fromString(fileUuid).toString();
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new IllegalArgumentException("文件单元 UUID 格式不正确", e);
        }

        String safeOriginalFilename = normalizeFilename(originalFilename);
        String storedFilename = normalizedFileUuid + "_" + safeOriginalFilename;
        return new OriginalFilePath(
                safeOriginalFilename,
                username + "/" + safeOriginalFilename
                        + "/original/" + storedFilename);
    }

    /**
     * 根据 original 逻辑路径生成同一文件单元下固定的 converted Markdown 路径。
     *
     * <p>路径只做确定性映射，不访问存储。重复转换同一个原文件会得到同一个目标路径，
     * 因而由存储实现覆盖旧的派生 Markdown。</p>
     *
     * @param originalFilePath 已落库的 original 相对路径或 MinIO object key
     * @return 同文件单元 converted 目录下、后缀固定为 {@code .md} 的 Markdown 逻辑路径
     * @throws IllegalArgumentException 路径不合法或不位于 original 目录
     */
    public static String createConvertedMarkdownPath(String originalFilePath) {
        return createConvertedMarkdownPath(originalFilePath, null);
    }

    /**
     * 根据 original 逻辑路径和转换服务返回的 Markdown 文件名生成 converted 路径。
     *
     * <p>转换服务负责确定 Markdown 文件名（固定 {@code .md} 后缀）；本方法只负责把
     * 文件单元 UUID 前缀拼回去，保证同一文件单元的 converted 对象唯一。</p>
     *
     * @param originalFilePath 已落库的 original 相对路径或 MinIO object key
     * @param markdownFilename 转换服务返回的 Markdown 文件名；为空时按原文件名推导
     * @return 同文件单元 converted 目录下的 Markdown 逻辑路径
     */
    public static String createConvertedMarkdownPath(
            String originalFilePath,
            String markdownFilename) {
        String normalizedPath = normalizeRelativePath(originalFilePath);
        String marker = "/original/";
        int markerIndex = normalizedPath.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalArgumentException("原始文件路径必须位于 original 目录");
        }

        String relativeFilename = normalizedPath.substring(
                markerIndex + marker.length());
        if (relativeFilename.isBlank()) {
            throw new IllegalArgumentException("原始文件路径必须位于 original 目录");
        }
        String targetFilename = markdownFilename == null
                || markdownFilename.isBlank()
                ? toMarkdownFilename(relativeFilename)
                : markdownFilename;
        return normalizedPath.substring(0, markerIndex)
                + "/converted/" + targetFilename;
    }

    /**
     * 生成投标记录级汇总 Markdown 的固定逻辑路径。
     *
     * <p>该路径是派生文件路径，不对应任何 {@code credential_file} 明细记录；每次风险分析
     * 前重新生成并覆盖同一个对象。</p>
     *
     * @param bidRecordId 投标记录 ID
     * @param username 投标方登录用户名
     * @return 汇总 Markdown 的逻辑存储路径
     */
    public static String createMergedMarkdownPath(
            Integer bidRecordId,
            String username) {
        validateBidRecordId(bidRecordId);
        validateUsername(username);
        return username
                + "/merged/" + bidRecordId + "/credentials.md";
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

    /** 校验用户名，文件路径必须以用户名为顶层归属。 */
    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
    }

    /** 将存储文件名统一转为 Markdown 文件名：去掉原扩展名并固定为 .md。 */
    private static String toMarkdownFilename(String storedFilename) {
        int lastDot = storedFilename.lastIndexOf('.');
        int lastSlash = storedFilename.lastIndexOf('/');
        if (lastDot > lastSlash + 1) {
            return storedFilename.substring(0, lastDot) + ".md";
        }
        return storedFilename + ".md";
    }

    /** 原始文件安全命名和逻辑路径结果。 */
    public record OriginalFilePath(String safeOriginalFilename, String relativePath) {
    }
}
