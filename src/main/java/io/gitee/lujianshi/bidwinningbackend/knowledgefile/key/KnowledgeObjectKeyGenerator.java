package io.gitee.lujianshi.bidwinningbackend.knowledgefile.key;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** 生成以 document_uuid 为根目录的 knowledge-file object key。 */
@Component
public class KnowledgeObjectKeyGenerator {

    /** 生成 {@code {uuid}/original/{uuid}_{safeFilename}}。 */
    public OriginalObjectKey original(
            UUID documentUuid,
            String originalFilename) {
        requireUuid(documentUuid);
        String safeFilename = normalizeFilename(originalFilename);
        String storedFilename = documentUuid + "_" + safeFilename;
        return new OriginalObjectKey(
                safeFilename,
                documentUuid + "/original/" + storedFilename);
    }

    /** 生成 {@code {uuid}/converted/content.md}。 */
    public String converted(UUID documentUuid) {
        requireUuid(documentUuid);
        return documentUuid + "/converted/content.md";
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
