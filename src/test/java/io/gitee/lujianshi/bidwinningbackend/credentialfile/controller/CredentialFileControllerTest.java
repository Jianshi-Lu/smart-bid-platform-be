package io.gitee.lujianshi.bidwinningbackend.credentialfile.controller;

import io.gitee.lujianshi.bidwinningbackend.controller.GlobalExceptionHandler;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.CredentialFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadRequest;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.CredentialFileBatchStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.UploadService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.CredentialFileBatchService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity.StoredFileIntegrityVerifier;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.PendingUploadItemProcessor;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CredentialFileControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDelegateMultipartUploadAndReturnTypedResponse() {
        CredentialFileController controller = createController();
        MockMultipartFile file = file();
        UploadRequest request = new UploadRequest();
        request.setBidRecordId(1001);
        request.setFiles(List.of(file));

        Result<UploadResponse> result = controller.upload(request);

        assertEquals(200, result.getCode());
        assertEquals("上传成功", result.getMessage());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getFiles().size());
    }

    @Test
    void shouldBindMultipartFieldsIntoUploadRequest() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(createController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(multipart("/credential-files/upload")
                        .file(file())
                        .param("bidRecordId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("上传成功"))
                .andExpect(jsonPath("$.data.files[0].originalFilename")
                        .value("credential.pdf"));
    }

    @Test
    void shouldRejectMultipartRequestWithoutBidRecordId() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(createController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(multipart("/credential-files/upload").file(file()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("投标记录 ID 不能为空"));
    }

    private CredentialFileController createController() {
        UploadService uploadService = new UploadService();
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        LocalFileStorage storage = new LocalFileStorage(properties);
        Sha256FileChecksumCalculator calculator = new Sha256FileChecksumCalculator();

        StoredFileIntegrityVerifier integrityVerifier =
                new StoredFileIntegrityVerifier();
        ReflectionTestUtils.setField(integrityVerifier, "fileStorage", storage);
        ReflectionTestUtils.setField(
                integrityVerifier, "fileChecksumCalculator", calculator);

        PendingUploadItemProcessor processor = new PendingUploadItemProcessor();
        ReflectionTestUtils.setField(processor, "fileStorage", storage);
        ReflectionTestUtils.setField(processor, "fileChecksumCalculator", calculator);
        ReflectionTestUtils.setField(
                processor, "storedFileIntegrityVerifier", integrityVerifier);

        ReflectionTestUtils.setField(
                uploadService, "pendingUploadItemProcessor", processor);
        ReflectionTestUtils.setField(
                uploadService, "credentialFileBatchService",
                new TestCredentialFileBatchService());
        ReflectionTestUtils.setField(uploadService, "fileStorage", storage);
        CredentialFileController controller = new CredentialFileController();
        ReflectionTestUtils.setField(controller, "uploadService", uploadService);
        return controller;
    }

    private static final class TestCredentialFileBatchService
            extends CredentialFileBatchService {

        @Override
        public CredentialFileBatch createUploadingBatch(
                Integer bidRecordId,
                int fileCount) {
            CredentialFileBatch batch = new CredentialFileBatch();
            batch.setUploadBatchId(UUID.randomUUID().toString());
            batch.setBidRecordId(bidRecordId);
            batch.setBatchNo(1);
            batch.setBatchStatus(CredentialFileBatchStatus.UPLOADING);
            batch.setFileCount(fileCount);
            batch.setConvertedFileCount(0);
            return batch;
        }

        @Override
        public void markUploaded(String uploadBatchId) {
            // Controller 测试只验证 multipart 到上传服务的调用链。
        }
    }

    private MockMultipartFile file() {
        return new MockMultipartFile(
                "files",
                "credential.pdf",
                "application/pdf",
                "test-content".getBytes(StandardCharsets.UTF_8));
    }
}
