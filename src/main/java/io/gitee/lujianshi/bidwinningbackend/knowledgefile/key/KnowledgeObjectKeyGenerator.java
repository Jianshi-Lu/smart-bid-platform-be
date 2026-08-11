package io.gitee.lujianshi.bidwinningbackend.knowledgefile.key;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** 生成以用户名为根目录、文件名为第二层的 knowledge-file object key。 */
@Component
public class KnowledgeObjectKeyGenerator {

    /** 生成 {@code {username}/{safeFilename}/original/{uuid}_{safeFilename}}。 */
    public OriginalObjectKey original(
            String username,
            UUID documentUuid,
            String originalFilename) {
        validateUsername(username);
        requireUuid(documentUuid);
        String safeFilename = normalizeFilename(originalFilename);
        String storedFilename = documentUuid + "_" + safeFilename;
        return new OriginalObjectKey(
                safeFilename,
                username + "/" + safeFilename + "/original/" + storedFilename);
    }

    /**
     * 生成 {@code {username}/{safeFilename}/converted/{uuid}_{markdownFilename}}。
     *
     * <p>Markdown 文件名由转换服务返回（固定 {@code .md} 后缀）。文件单元 UUID 必须
     * 保留在目标 key 中，避免同一账号上传同名文件时互相覆盖转换结果。</p>
     */
    public String converted(
            String username,
            UUID documentUuid,
            String safeOriginalFilename,
            String markdownFilename) {
        validateUsername(username);
        requireUuid(documentUuid);
        String safeFilename = normalizeFilename(safeOriginalFilename);
        String safeMarkdownFilename = normalizeFilename(markdownFilename);
        return username + "/" + safeFilename
                + "/converted/" + documentUuid + "_" + safeMarkdownFilename;
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
    }

    private void requireUuid(UUID documentUuid) {
        if (documentUuid == null) {
            throw new IllegalArgumentException("document_uuid 不能为空");
        }
    }

    private String normalizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("知识文件名不能为空");
        }
        String unified = originalFilename.replace('\\', '/');
        String filename = unified.substring(unified.lastIndexOf('/') + 1).trim();
        filename = filename.replaceAll("[\\p{Cntrl}]", "_");
        if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
            throw new IllegalArgumentException("知识文件名不合法");
        }
        return filename;
    }

    /** original 安全文件名与最终 object key。 */
    public record OriginalObjectKey(
            String safeOriginalFilename,
            String objectKey) {
    }
}
