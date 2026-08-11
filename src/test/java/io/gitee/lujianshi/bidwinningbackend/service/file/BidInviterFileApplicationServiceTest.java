package io.gitee.lujianshi.bidwinningbackend.service.file;

import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileConvertService;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.dto.KnowledgeFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.service.KnowledgeFileIngestionService;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import io.gitee.lujianshi.bidwinningbackend.service.audit.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.service.file.FileDownloadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BidInviterFileApplicationServiceTest {

    @AfterEach
    void clearContext() {
        CurrentAccountContext.clear();
    }

    @Test
    void shouldDelegateKnowledgeUploadForAuthenticatedInviter() {
        KnowledgeFileIngestionService ingestion =
                mock(KnowledgeFileIngestionService.class);
        BidInviterFileApplicationService service = new BidInviterFileApplicationService(
                ingestion,
                mock(FileConvertService.class),
                mock(FileUploadService.class),
                mock(OperationHistoryService.class),
                mock(FileStatusEventPublisher.class),
                mock(FileDownloadService.class));
        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BID_INVITER, 3L, "inviter01"));
        List<KnowledgeFileUpload> files = List.of(new KnowledgeFileUpload(
                "policy.pdf",
                "application/pdf",
                3,
                () -> new ByteArrayInputStream(new byte[] {1, 2, 3})));
        KnowledgeFileUploadResponse expected =
                new KnowledgeFileUploadResponse(List.of());
        when(ingestion.ingest(files, "inviter01")).thenReturn(expected);

        KnowledgeFileUploadResponse actual = service.uploadKnowledgeFiles(
                files, "inviter01");

        assertSame(expected, actual);
    }
}
