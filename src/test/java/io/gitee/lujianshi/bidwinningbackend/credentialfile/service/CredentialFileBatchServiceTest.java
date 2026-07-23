package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.CredentialFileBatchStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileBatchMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialFileBatchServiceTest {

    @Test
    void shouldCreateBackendGeneratedBatchWithNextBatchNo() {
        CredentialFileBatchMapper mapper = mock(CredentialFileBatchMapper.class);
        when(mapper.selectMaxBatchNo(1001)).thenReturn(2);
        when(mapper.insert(any(CredentialFileBatch.class))).thenReturn(1);
        CredentialFileBatchService service = createService(mapper);

        CredentialFileBatch batch = service.createUploadingBatch(1001, 3);

        assertEquals(1001, batch.getBidRecordId());
        assertEquals(3, batch.getBatchNo());
        assertEquals(CredentialFileBatchStatus.UPLOADING, batch.getBatchStatus());
        assertEquals(3, batch.getFileCount());
        assertEquals(0, batch.getConvertedFileCount());
        assertNotNull(batch.getUploadBatchId());
        verify(mapper).insert(batch);
    }

    @Test
    void shouldMarkBatchUploadedOnlyWhenItIsStillUploading() {
        CredentialFileBatchMapper mapper = mock(CredentialFileBatchMapper.class);
        when(mapper.markUploaded(
                any(String.class),
                eq(CredentialFileBatchStatus.UPLOADED.name()),
                any(LocalDateTime.class))).thenReturn(1);
        CredentialFileBatchService service = createService(mapper);

        service.markUploaded("batch-id");

        verify(mapper).markUploaded(
                eq("batch-id"),
                eq(CredentialFileBatchStatus.UPLOADED.name()),
                any(LocalDateTime.class));
    }

    @Test
    void shouldRejectInvalidBatchCreationArguments() {
        CredentialFileBatchService service = createService(mock(CredentialFileBatchMapper.class));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createUploadingBatch(0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createUploadingBatch(1001, 0));
    }

    private CredentialFileBatchService createService(CredentialFileBatchMapper mapper) {
        CredentialFileBatchService service = new CredentialFileBatchService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        return service;
    }
}
