package io.gitee.lujianshi.bidwinningbackend.service.file;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.storage.object.ObjectStorage;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.KnowledgeFileMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 受保护的文件下载服务。
 *
 * <p>Controller 负责角色鉴权和业务归属校验，本服务负责读取数据库事实源、
 * 判断文件是否已经达到可下载状态，并通过对应的存储适配器打开对象。
 * 存储路径不会直接暴露给前端。</p>
 */
@Service
public class FileDownloadService {

    private static final String CONVERTED = "CONVERTED";
    private static final String MARKDOWN_CONTENT_TYPE =
            "text/markdown; charset=UTF-8";

    private final CredentialFileMapper credentialFileMapper;
    private final KnowledgeFileMapper knowledgeFileMapper;
    private final ObjectStorage credentialFileStorage;
    private final ObjectStorage knowledgeFileStorage;

    public FileDownloadService(
            CredentialFileMapper credentialFileMapper,
            KnowledgeFileMapper knowledgeFileMapper,
            @Qualifier("credentialFileStorage") ObjectStorage credentialFileStorage,
            @Qualifier("knowledgeFileStorage") ObjectStorage knowledgeFileStorage) {
        this.credentialFileMapper = credentialFileMapper;
        this.knowledgeFileMapper = knowledgeFileMapper;
        this.credentialFileStorage = credentialFileStorage;
        this.knowledgeFileStorage = knowledgeFileStorage;
    }

    /** 打开资信文件原始对象；bidRecordId 不为空时同时校验文件归属。 */
    public DownloadResource openCredentialOriginal(
            Long fileId,
            Integer bidRecordId) {
        CredentialFile file = requireCredential(fileId);
        if (bidRecordId != null
                && (file.getBidRecordId() == null
                || !bidRecordId.equals(file.getBidRecordId()))) {
            throw credentialNotFound(fileId);
        }
        requireReady(
                file.getOriginalFilePath(),
                file.getOriginalSha256(),
                AppErrorCode.CREDENTIAL_FILE_DOWNLOAD_NOT_READY,
                "资信文件原始内容尚未完成上传");
        return open(
                credentialFileStorage,
                file.getOriginalFilePath(),
                safeOriginalFilename(file.getOriginalFilename()),
                mediaType(file.getContentType()),
                file.getFileSize(),
                AppErrorCode.CREDENTIAL_FILE_STORAGE_FAILED,
                "CREDENTIAL_ORIGINAL");
    }

    /** 打开资信文件转换后的 Markdown 对象；bidRecordId 不为空时同时校验文件归属。 */
    public DownloadResource openCredentialMarkdown(
            Long fileId,
            Integer bidRecordId) {
        CredentialFile file = requireCredential(fileId);
        if (bidRecordId != null
                && (file.getBidRecordId() == null
                || !bidRecordId.equals(file.getBidRecordId()))) {
            throw credentialNotFound(fileId);
        }
        requireMarkdownReady(
                file.getMarkdownFilePath(),
                file.getConvertedSha256(),
                file.getConvertStatus() == ConvertStatus.CONVERTED
                        ? CONVERTED
                        : null,
                AppErrorCode.CREDENTIAL_FILE_DOWNLOAD_NOT_READY,
                "资信文件 Markdown 尚未转换完成");
        return open(
                credentialFileStorage,
                file.getMarkdownFilePath(),
                markdownFilename(file.getOriginalFilename()),
                MediaType.parseMediaType(MARKDOWN_CONTENT_TYPE),
                null,
                AppErrorCode.CREDENTIAL_FILE_STORAGE_FAILED,
                "CREDENTIAL_MARKDOWN");
    }

    /** 打开知识文件原始对象。 */
    public DownloadResource openKnowledgeOriginal(String documentUuid) {
        KnowledgeFileRecord file = requireKnowledge(documentUuid);
        requireReady(
                file.getOriginalFilePath(),
                file.getOriginalSha256(),
                AppErrorCode.KNOWLEDGE_FILE_DOWNLOAD_NOT_READY,
                "知识文件原始内容尚未完成上传");
        return open(
                knowledgeFileStorage,
                file.getOriginalFilePath(),
                safeOriginalFilename(file.getOriginalFilename()),
                mediaType(file.getContentType()),
                file.getFileSize(),
                AppErrorCode.KNOWLEDGE_FILE_STORAGE_FAILED,
                "KNOWLEDGE_ORIGINAL");
    }

    /** 打开知识文件转换后的 Markdown 对象。 */
    public DownloadResource openKnowledgeMarkdown(String documentUuid) {
        KnowledgeFileRecord file = requireKnowledge(documentUuid);
        requireMarkdownReady(
                file.getMarkdownFilePath(),
                file.getConvertedSha256(),
                file.getConvertStatus(),
                AppErrorCode.KNOWLEDGE_FILE_DOWNLOAD_NOT_READY,
                "知识文件 Markdown 尚未转换完成");
        return open(
                knowledgeFileStorage,
                file.getMarkdownFilePath(),
                markdownFilename(file.getOriginalFilename()),
                MediaType.parseMediaType(MARKDOWN_CONTENT_TYPE),
                null,
                AppErrorCode.KNOWLEDGE_FILE_STORAGE_FAILED,
                "KNOWLEDGE_MARKDOWN");
    }

    /** 将已打开的文件流包装为浏览器下载响应，并在传输完成后关闭流。 */
    public ResponseEntity<StreamingResponseBody> response(DownloadResource resource) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(resource.contentType());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(resource.filename(), StandardCharsets.UTF_8)
                .build());
        if (resource.contentLength() != null) {
            headers.setContentLength(resource.contentLength());
        }

        StreamingResponseBody body = output -> {
            try (InputStream input = resource.input()) {
                input.transferTo(output);
            }
        };
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private CredentialFile requireCredential(Long fileId) {
        CredentialFile file = fileId == null
                ? null
                : credentialFileMapper.selectById(fileId);
        if (file == null || unavailable(file.getDeletedAt(), file.getDeletionStatus())) {
            throw credentialNotFound(fileId);
        }
        return file;
    }

    private KnowledgeFileRecord requireKnowledge(String documentUuid) {
        KnowledgeFileRecord file = documentUuid == null || documentUuid.isBlank()
                ? null
                : knowledgeFileMapper.selectByDocumentUuid(documentUuid);
        if (file == null
                || unavailable(file.getDeletedAt(), file.getDeletionStatus())) {
            throw new AppException(
                    AppErrorCode.KNOWLEDGE_FILE_NOT_FOUND,
                    "knowledge-file",
                    "DOWNLOAD",
                    "LOAD_RECORD",
                    "知识文件不存在");
        }
        return file;
    }

    private boolean unavailable(
            java.time.LocalDateTime deletedAt,
            String deletionStatus) {
        return deletedAt != null
                || "DELETING".equals(deletionStatus)
                || "DELETED".equals(deletionStatus);
    }

    private void requireReady(
            String path,
            String sha256,
            AppErrorCode errorCode,
            String message) {
        if (path == null || path.isBlank() || sha256 == null || sha256.isBlank()) {
            throw new AppException(
                    errorCode,
                    errorCode == AppErrorCode.CREDENTIAL_FILE_DOWNLOAD_NOT_READY
                            ? "credential-file"
                            : "knowledge-file",
                    "DOWNLOAD",
                    "CHECK_READY",
                    message);
        }
    }

    private void requireMarkdownReady(
            String path,
            String sha256,
            String convertStatus,
            AppErrorCode errorCode,
            String message) {
        if (!CONVERTED.equals(convertStatus)
                || path == null
                || path.isBlank()
                || sha256 == null
                || sha256.isBlank()) {
            throw new AppException(
                    errorCode,
                    errorCode == AppErrorCode.CREDENTIAL_FILE_DOWNLOAD_NOT_READY
                            ? "credential-file"
                            : "knowledge-file",
                    "DOWNLOAD",
                    "CHECK_MARKDOWN_READY",
                    message);
        }
    }

    private DownloadResource open(
            ObjectStorage storage,
            String path,
            String filename,
            MediaType contentType,
            Long contentLength,
            AppErrorCode storageError,
            String operation) {
        try {
            return new DownloadResource(
                    storage.open(new ObjectStorage.OpenRequest(path, null)),
                    filename,
                    contentType,
                    contentLength);
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new AppException(
                    storageError,
                    storageError == AppErrorCode.CREDENTIAL_FILE_STORAGE_FAILED
                            ? "credential-file"
                            : "knowledge-file",
                    "DOWNLOAD",
                    operation,
                    "文件存储对象暂不可读",
                    e);
        }
    }

    private AppException credentialNotFound(Long fileId) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_NOT_FOUND,
                "credential-file",
                "DOWNLOAD",
                "LOAD_RECORD",
                "资信文件不存在: " + fileId);
    }

    private MediaType mediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String safeOriginalFilename(String value) {
        if (value == null || value.isBlank()) {
            return "download";
        }
        String filename = value.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        filename = filename.replaceAll("[\\r\\n\\\"]", "_").trim();
        return filename.isBlank() ? "download" : filename;
    }

    private String markdownFilename(String originalFilename) {
        String filename = safeOriginalFilename(originalFilename);
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base + ".md";
    }

    /** 已通过数据库就绪校验且已打开的受保护文件流。 */
    public record DownloadResource(
            InputStream input,
            String filename,
            MediaType contentType,
            Long contentLength) {
    }
}
