package io.gitee.lujianshi.bidwinningbackend.service.file;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.storage.object.ObjectStorage;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.KnowledgeFileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileDownloadServiceTest {

    private final CredentialFileMapper credentialFileMapper =
            mock(CredentialFileMapper.class);
    private final KnowledgeFileMapper knowledgeFileMapper =
            mock(KnowledgeFileMapper.class);
    private final ObjectStorage credentialStorage = mock(ObjectStorage.class);
    private final ObjectStorage knowledgeStorage = mock(ObjectStorage.class);

    private final FileDownloadService service = new FileDownloadService(
            credentialFileMapper,
            knowledgeFileMapper,
            credentialStorage,
            knowledgeStorage);

    @Test
    void originalDownloadRequiresConfirmedUploadMetadata() {
        CredentialFile file = credentialFile();
        file.setOriginalFilePath(null);
        file.setOriginalSha256(null);
        when(credentialFileMapper.selectById(1L)).thenReturn(file);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.openCredentialOriginal(1L, 7));

        assertEquals(
                AppErrorCode.CREDENTIAL_FILE_DOWNLOAD_NOT_READY,
                exception.getErrorCode());
    }

    @Test
    void markdownDownloadRequiresConvertedStatusAndChecksum() {
        CredentialFile file = credentialFile();
        file.setMarkdownFilePath("7/files/file/converted.md");
        file.setConvertedSha256("converted-sha");
        file.setConvertStatus(ConvertStatus.FAILED);
        when(credentialFileMapper.selectById(1L)).thenReturn(file);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.openCredentialMarkdown(1L, 7));

        assertEquals(
                AppErrorCode.CREDENTIAL_FILE_DOWNLOAD_NOT_READY,
                exception.getErrorCode());
    }

    @Test
    void knowledgeOriginalStreamsWithAttachmentHeaders() throws Exception {
        KnowledgeFileRecord file = new KnowledgeFileRecord();
        file.setDocumentUuid("doc-1");
        file.setOriginalFilename("资料.pdf");
        file.setContentType("application/pdf");
        file.setFileSize(5L);
        file.setOriginalFilePath("doc-1/original/source");
        file.setOriginalSha256("original-sha");
        when(knowledgeFileMapper.selectByDocumentUuid("doc-1")).thenReturn(file);
        when(knowledgeStorage.open(any(ObjectStorage.OpenRequest.class)))
                .thenReturn(new java.io.ByteArrayInputStream(
                        "hello".getBytes(StandardCharsets.UTF_8)));

        FileDownloadService.DownloadResource resource =
                service.openKnowledgeOriginal("doc-1");
        var response = service.response(resource);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertEquals("hello", output.toString(StandardCharsets.UTF_8));
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("资料.pdf", response.getHeaders().getContentDisposition().getFilename());
        assertEquals(5L, response.getHeaders().getContentLength());
    }

    private CredentialFile credentialFile() {
        CredentialFile file = new CredentialFile();
        file.setFileId(1L);
        file.setBidRecordId(7);
        file.setOriginalFilename("资料.docx");
        file.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        file.setFileSize(10L);
        file.setOriginalFilePath("7/files/file/original/source");
        file.setOriginalSha256("original-sha");
        file.setConvertStatus(ConvertStatus.CONVERTED);
        return file;
    }
}
