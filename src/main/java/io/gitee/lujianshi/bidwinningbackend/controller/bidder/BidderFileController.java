package io.gitee.lujianshi.bidwinningbackend.controller.bidder;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.file.FileDownloadService;
import io.gitee.lujianshi.bidwinningbackend.service.file.BidderFileApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/** 投标方资信文件的 HTTP 适配入口；鉴权与生命周期交给应用服务。 */
@RestController("bidderFileController")
@RequestMapping("/bidder/bid-records")
public class BidderFileController {

    private final BidderFileApplicationService applicationService;
    private final FileDownloadService fileDownloadService;

    public BidderFileController(
            BidderFileApplicationService applicationService,
            FileDownloadService fileDownloadService) {
        this.applicationService = applicationService;
        this.fileDownloadService = fileDownloadService;
    }

    @PostMapping(value = "/{id}/credential-files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadResponse> uploadCredentialFiles(
            @PathVariable Integer id,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("username") String username,
            HttpServletRequest request) {
        return Result.success(
                applicationService.uploadCredentialFiles(
                        id,
                        username,
                        authorization(request),
                        toUploadItems(files)),
                "上传成功");
    }

    @DeleteMapping("/{id}/credential-files/{fileId}")
    public Result<Void> deleteCredentialFile(
            @PathVariable Integer id,
            @PathVariable Long fileId,
            HttpServletRequest request) {
        applicationService.deleteCredentialFile(
                id, fileId, authorization(request));
        return Result.success(null, "删除成功");
    }

    @GetMapping("/{id}/credential-files")
    public Result<List<CredentialFile>> listCredentialFiles(
            @PathVariable Integer id,
            HttpServletRequest request) {
        return Result.success(
                applicationService.listCredentialFiles(
                        id, authorization(request)),
                "查询成功");
    }

    @GetMapping(
            value = "/{id}/credential-files/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter credentialFileEvents(
            @PathVariable Integer id,
            HttpServletRequest request) {
        return applicationService.subscribeCredentialFileEvents(
                id, authorization(request));
    }

    @GetMapping("/{bidRecordId}/credential-files/{fileId}/download/original")
    public ResponseEntity<StreamingResponseBody> downloadOriginal(
            @PathVariable Integer bidRecordId,
            @PathVariable Long fileId,
            HttpServletRequest request) {
        return fileDownloadService.response(
                applicationService.openCredentialOriginal(
                        bidRecordId, fileId, authorization(request)));
    }

    @GetMapping("/{bidRecordId}/credential-files/{fileId}/download/markdown")
    public ResponseEntity<StreamingResponseBody> downloadMarkdown(
            @PathVariable Integer bidRecordId,
            @PathVariable Long fileId,
            HttpServletRequest request) {
        return fileDownloadService.response(
                applicationService.openCredentialMarkdown(
                        bidRecordId, fileId, authorization(request)));
    }

    private String authorization(HttpServletRequest request) {
        return request.getHeader("Authorization");
    }

    private List<UploadItem> toUploadItems(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw invalidUpload("上传资信文件不能为空");
        }
        return files.stream()
                .map(file -> {
                    if (file == null) {
                        throw invalidUpload("上传文件不能为空");
                    }
                    try {
                        return new UploadItem(
                                file.getOriginalFilename(),
                                file.getContentType(),
                                file.getSize(),
                                file::getInputStream);
                    } catch (IllegalArgumentException e) {
                        throw invalidUpload("上传文件不合法");
                    }
                })
                .toList();
    }

    private AppException invalidUpload(String message) {
        return new AppException(
                AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                "credential-file",
                "UPLOAD",
                "VALIDATE_REQUEST",
                message);
    }
}
