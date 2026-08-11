package io.gitee.lujianshi.bidwinningbackend.storage.object;

/** 对本地相对路径和 MinIO object key 执行一致的安全规范化。 */
public final class ObjectKey {

    private ObjectKey() {
    }

    /**
     * 统一路径分隔符并拒绝绝对路径、空路径段和目录穿越。
     *
     * @param value 本地相对路径或 MinIO object key
     * @return 使用正斜杠的安全 object key
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("文件相对路径不能为空");
        }

        String normalized = value.replace('\\', '/');
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
}
