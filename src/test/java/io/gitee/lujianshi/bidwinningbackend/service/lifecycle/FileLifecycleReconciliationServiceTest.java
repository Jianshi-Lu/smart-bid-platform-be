package io.gitee.lujianshi.bidwinningbackend.service.lifecycle;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.service.KnowledgeFileIngestionService;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.KnowledgeFileMapper;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordLifecycleService;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordVectorIndexService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileLifecycleReconciliationServiceTest {

    @Test
    void shouldContinueOtherResourcesWhenOneReconciliationFails() {
        CredentialFileMapper credentialMapper = mock(CredentialFileMapper.class);
        KnowledgeFileMapper knowledgeMapper = mock(KnowledgeFileMapper.class);
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        FileUploadService credentialService = mock(FileUploadService.class);
        KnowledgeFileIngestionService knowledgeService =
                mock(KnowledgeFileIngestionService.class);
        BidRecordVectorIndexService bidIndexService =
                mock(BidRecordVectorIndexService.class);
        BidRecordLifecycleService bidLifecycleService =
                mock(BidRecordLifecycleService.class);

        CredentialFile credential = new CredentialFile();
        credential.setFileId(71L);
        KnowledgeFileRecord knowledge = new KnowledgeFileRecord();
        knowledge.setDocumentUuid("doc-1");
        BidRecord bidRecord = new BidRecord();
        bidRecord.setBidRecordId(13);
        when(credentialMapper.selectAll()).thenReturn(List.of(credential));
        when(knowledgeMapper.selectAll()).thenReturn(List.of(knowledge));
        when(bidRecordMapper.selectAll()).thenReturn(List.of(bidRecord));
        doThrow(new IllegalStateException("minio unavailable"))
                .when(credentialService).reconcileLifecycle(credential);

        FileLifecycleReconciliationService service =
                new FileLifecycleReconciliationService(
                        credentialMapper,
                        knowledgeMapper,
                        bidRecordMapper,
                        credentialService,
                        knowledgeService,
                        bidIndexService,
                        bidLifecycleService);

        var report = service.reconcileAll();

        assertEquals(0, report.bidRecordDeletions());
        assertEquals(1, report.credentialFiles());
        assertEquals(1, report.knowledgeFiles());
        assertEquals(1, report.bidRecords());
        assertEquals(1, report.failures().size());
        assertTrue(report.failures().getFirst().contains("credential_file:71"));
        verify(knowledgeService).reconcileLifecycle(knowledge);
        verify(credentialService).reconcileBidRecordProjection(13);
        verify(bidIndexService).reindex(13);
    }
}
