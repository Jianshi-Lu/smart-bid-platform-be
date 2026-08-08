package io.gitee.lujianshi.bidwinningbackend.controller.bid_inviter;

import io.gitee.lujianshi.bidwinningbackend.service.FileDownloadService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** 招标方受现有 Basic 鉴权拦截器保护的文件下载入口。 */
@RestController("bidInviterFileDownloadController")
public class BidInviterFileDownloadController {

    @Resource
    private FileDownloadService fileDownloadService;

    @GetMapping("/credential-files/{fileId}/download/original")
    public ResponseEntity<StreamingResponseBody> downloadCredentialOriginal(
            @PathVariable Long fileId) {
        return fileDownloadService.response(
                fileDownloadService.openCredentialOriginal(fileId, null));
    }

    @GetMapping("/credential-files/{fileId}/download/markdown")
    public ResponseEntity<StreamingResponseBody> downloadCredentialMarkdown(
            @PathVariable Long fileId) {
        return fileDownloadService.response(
                fileDownloadService.openCredentialMarkdown(fileId, null));
    }

    @GetMapping("/knowledge-files/{documentUuid}/download/original")
    public ResponseEntity<StreamingResponseBody> downloadKnowledgeOriginal(
            @PathVariable String documentUuid) {
        return fileDownloadService.response(
                fileDownloadService.openKnowledgeOriginal(documentUuid));
    }

    @GetMapping("/knowledge-files/{documentUuid}/download/markdown")
    public ResponseEntity<StreamingResponseBody> downloadKnowledgeMarkdown(
            @PathVariable String documentUuid) {
        return fileDownloadService.response(
                fileDownloadService.openKnowledgeMarkdown(documentUuid));
    }
}
