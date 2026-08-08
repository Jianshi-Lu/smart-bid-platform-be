package io.gitee.lujianshi.bidwinningbackend.controller.bidder;

import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.BidRecordService;
import io.gitee.lujianshi.bidwinningbackend.service.BidderAccountService;
import io.gitee.lujianshi.bidwinningbackend.service.FileDownloadService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** 投标方受保护的资信文件下载入口。 */
@RestController
@RequestMapping("/bidder/bid-records")
public class BidderFileDownloadController {

    @Resource
    private BidRecordService bidRecordService;

    @Resource
    private BidderAccountService bidderAccountService;

    @Resource
    private FileDownloadService fileDownloadService;

    @GetMapping("/{bidRecordId}/credential-files/{fileId}/download/original")
    public ResponseEntity<StreamingResponseBody> downloadOriginal(
            @PathVariable Integer bidRecordId,
            @PathVariable Long fileId,
            HttpServletRequest request) {
        requireOwned(request, bidRecordId);
        return fileDownloadService.response(
                fileDownloadService.openCredentialOriginal(fileId, bidRecordId));
    }

    @GetMapping("/{bidRecordId}/credential-files/{fileId}/download/markdown")
    public ResponseEntity<StreamingResponseBody> downloadMarkdown(
            @PathVariable Integer bidRecordId,
            @PathVariable Long fileId,
            HttpServletRequest request) {
        requireOwned(request, bidRecordId);
        return fileDownloadService.response(
                fileDownloadService.openCredentialMarkdown(fileId, bidRecordId));
    }

    private void requireOwned(HttpServletRequest request, Integer bidRecordId) {
        BidderAccount account = bidderAccountService.authenticateBasic(
                request.getHeader("Authorization"));
        BidRecord record = bidRecordService.getById(bidRecordId);
        if (record == null) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_NOT_FOUND,
                    "bid-record",
                    "DOWNLOAD",
                    "LOAD_RECORD",
                    "投标记录不存在");
        }
        if (record.getBidderAccountId() == null
                || !record.getBidderAccountId().equals(account.getBidderAccountId())) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_FORBIDDEN,
                    "bid-record",
                    "DOWNLOAD",
                    "CHECK_OWNERSHIP",
                    "无权下载该投标记录的资信文件");
        }
    }
}
