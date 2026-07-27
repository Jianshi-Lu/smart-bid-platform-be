package io.gitee.lujianshi.bidwinningbackend.credentialfile.controller;

import io.gitee.lujianshi.bidwinningbackend.controller.GlobalExceptionHandler;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadRequest;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileBatchMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.UploadItemProcessor;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileUploadControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDelegateMultipartUploadAndReturnTypedResponse() {
        FileUploadController controller = createController();
        MockMultipartFile file = file();
        UploadRequest request = new UploadRequest();
        request.setBidRecordId(1001);
        request.setFiles(List.of(file));

        Result<UploadResponse> result = controller.upload(request);

        assertEquals("SUCCESS", result.getCode());
        assertEquals(true, result.isSuccess());
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
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.success").value(true))
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON.INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("投标记录 ID 不能为空"));
    }

    private FileUploadController createController() {
        FileUploadService uploadService = new FileUploadService();
        FileStorageProperties properties = new FileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        LocalFileStorage storage = new LocalFileStorage(properties);
        Sha256FileChecksumCalculator calculator = new Sha256FileChecksumCalculator();

        UploadItemProcessor processor = new UploadItemProcessor();
        ReflectionTestUtils.setField(processor, "fileStorage", storage);
        ReflectionTestUtils.setField(processor, "fileChecksumCalculator", calculator);

        ReflectionTestUtils.setField(
                uploadService, "uploadItemProcessor", processor);
        CredentialFileBatchMapper batchMapper = mock(CredentialFileBatchMapper.class);
        when(batchMapper.insert(any(CredentialFileBatch.class))).thenReturn(1);
        when(batchMapper.markUploaded(
                anyString(), anyString(), any())).thenReturn(1);
        ReflectionTestUtils.setField(
                uploadService, "credentialFileBatchMapper", batchMapper);
        ReflectionTestUtils.setField(uploadService, "fileStorage", storage);
        ReflectionTestUtils.setField(uploadService, "storageProperties", properties);
        ReflectionTestUtils.setField(uploadService, "credentialFileMapper", mock(CredentialFileMapper.class));
        FileUploadController controller = new FileUploadController();
        ReflectionTestUtils.setField(controller, "uploadService", uploadService);
        return controller;
    }

    private MockMultipartFile file() {
        return new MockMultipartFile(
                "files",
                "credential.pdf",
                "application/pdf",
                "test-content".getBytes(StandardCharsets.UTF_8));
    }
}
