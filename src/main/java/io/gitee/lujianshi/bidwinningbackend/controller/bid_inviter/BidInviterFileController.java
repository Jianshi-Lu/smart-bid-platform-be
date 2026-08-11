package io.gitee.lujianshi.bidwinningbackend.controller.bid_inviter;

import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.dto.KnowledgeFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.service.file.FileDownloadService;
import io.gitee.lujianshi.bidwinningbackend.service.file.BidInviterFileApplicationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/** 招标方知识文件和投标资信文件的统一 HTTP 适配入口。 */
@RestController("bidInviterFileController")
public class BidInviterFileController {

    private final BidInviterFileApplicationService applicationService;
    private final FileDownloadService fileDownloadService;

    public BidInviterFileController(
            BidInviterFileApplicationService applicationService,
            FileDownloadService fileDownloadService) {
        this.applicationService = applicationService;
        this.fileDownloadService = fileDownloadService;
    }

    @PostMapping(
            value = "/knowledge-files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeFileUploadResponse> uploadKnowledgeFiles(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam("username") String username) {
        return Result.success(
                applicationService.uploadKnowledgeFiles(
                        toKnowledgeFileUploads(files), username),
                "知识文件处理完成");
    }

    @GetMapping("/knowledge-files")
    public Result<PageInfo<KnowledgeFileRecord>> listKnowledgeFiles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "created_at desc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String convertStatus,
            @RequestParam(required = false) String indexStatus) {
        return Result.success(
                applicationService.listKnowledgeFiles(
                        page,
                        pageSize,
                        sort,
                        keyword,
                        convertStatus,
                        indexStatus),
                "查询成功");
    }

    @GetMapping("/knowledge-files/{documentUuid}")
    public Result<KnowledgeFileRecord> knowledgeFileDetail(
            @PathVariable String documentUuid) {
        return Result.success(
                applicationService.getKnowledgeFile(documentUuid),
                "查询成功");
    }

    @GetMapping("/knowledge-files/{documentUuid}/history")
    public Result<List<KnowledgeFileOperationHistory>> listKnowledgeFileHistory(
            @PathVariable String documentUuid) {
        return Result.success(
                applicationService.listKnowledgeFileHistory(documentUuid),
                "查询成功");
    }

    @GetMapping(
            value = "/knowledge-files/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter knowledgeFileEvents() {
        return applicationService.subscribeKnowledgeFileEvents();
    }

    @PostMapping("/knowledge-files/{documentUuid}/retry")
    public Result<KnowledgeFileRecord> retryKnowledgeFile(
            @PathVariable String documentUuid) {
        return Result.success(
                applicationService.retryKnowledgeFile(documentUuid),
                "重试完成");
    }

    @DeleteMapping("/knowledge-files/{documentUuid}")
    public Result<Void> deleteKnowledgeFile(
            @PathVariable String documentUuid) {
        applicationService.deleteKnowledgeFile(documentUuid);
        return Result.success(null, "删除成功");
    }

    @PostMapping("/credential-files/bid-records/{bidRecordId}/convert")
    public Result<FileConvertResponse> convertCredentialFiles(
            @PathVariable Integer bidRecordId) {
        FileConvertResponse response =
                applicationService.convertCredentialFiles(bidRecordId);
        return Result.success(response, response.message());
    }

    /** 供招标方 Agent 对话中的资料引用加载可下载的资信文件。 */
    @GetMapping("/credential-files/bid-records/{bidRecordId}")
    public Result<List<CredentialFile>> listCredentialFiles(
            @PathVariable Integer bidRecordId) {
        return Result.success(
                applicationService.listCredentialFiles(bidRecordId),
                "查询成功");
    }

    @GetMapping("/credential-files/{fileId}/history")
    public Result<List<CredentialFileOperationHistory>> listCredentialFileHistory(
            @PathVariable Long fileId) {
        return Result.success(
                applicationService.listCredentialFileHistory(fileId),
                "查询成功");
    }

    @GetMapping("/credential-files/{fileId}/download/original")
    public ResponseEntity<StreamingResponseBody> downloadCredentialOriginal(
            @PathVariable Long fileId) {
        return fileDownloadService.response(
                applicationService.openCredentialOriginal(fileId));
    }

    @GetMapping("/credential-files/{fileId}/download/markdown")
    public ResponseEntity<StreamingResponseBody> downloadCredentialMarkdown(
            @PathVariable Long fileId) {
        return fileDownloadService.response(
                applicationService.openCredentialMarkdown(fileId));
    }

    @GetMapping("/knowledge-files/{documentUuid}/download/original")
    public ResponseEntity<StreamingResponseBody> downloadKnowledgeOriginal(
            @PathVariable String documentUuid) {
        return fileDownloadService.response(
                applicationService.openKnowledgeOriginal(documentUuid));
    }

    @GetMapping("/knowledge-files/{documentUuid}/download/markdown")
    public ResponseEntity<StreamingResponseBody> downloadKnowledgeMarkdown(
            @PathVariable String documentUuid) {
        return fileDownloadService.response(
                applicationService.openKnowledgeMarkdown(documentUuid));
    }

    private List<KnowledgeFileUpload> toKnowledgeFileUploads(
            List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw invalidKnowledgeFile("上传知识文件不能为空", null);
        }
        return files.stream()
                .map(file -> {
                    if (file == null) {
                        throw invalidKnowledgeFile(
                                "上传知识文件不能为空", null);
                    }
                    try {
                        return new KnowledgeFileUpload(
                                file.getOriginalFilename(),
                                file.getContentType(),
                                file.getSize(),
                                file::getInputStream);
                    } catch (IllegalArgumentException failure) {
                        throw invalidKnowledgeFile(
                                "上传知识文件不合法", failure);
                    }
                })
                .toList();
    }

    private AppException invalidKnowledgeFile(
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.KNOWLEDGE_FILE_INVALID_FILE,
                "knowledge-file",
                "INGEST",
                "VALIDATE_FILE",
                message,
                cause);
    }
}
