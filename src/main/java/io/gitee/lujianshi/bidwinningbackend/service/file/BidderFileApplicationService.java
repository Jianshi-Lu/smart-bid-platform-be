package io.gitee.lujianshi.bidwinningbackend.service.file;

import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import io.gitee.lujianshi.bidwinningbackend.service.account.BidderAccountService;
import io.gitee.lujianshi.bidwinningbackend.service.file.FileDownloadService;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.function.Supplier;

/** 投标方文件入口的账号鉴权、记录归属与资信文件生命周期编排。 */
@Service
public class BidderFileApplicationService {

    private final BidRecordService bidRecordService;
    private final FileUploadService fileUploadService;
    private final BidderAccountService bidderAccountService;
    private final FileStatusEventPublisher fileStatusEventPublisher;
    private final FileDownloadService fileDownloadService;

    public BidderFileApplicationService(
            BidRecordService bidRecordService,
            FileUploadService fileUploadService,
            BidderAccountService bidderAccountService,
            FileStatusEventPublisher fileStatusEventPublisher,
            FileDownloadService fileDownloadService) {
        this.bidRecordService = bidRecordService;
        this.fileUploadService = fileUploadService;
        this.bidderAccountService = bidderAccountService;
        this.fileStatusEventPublisher = fileStatusEventPublisher;
        this.fileDownloadService = fileDownloadService;
    }

    /** 校验当前账号及记录归属后，批量进入资信材料上传生命周期。 */
    public UploadResponse uploadCredentialFiles(
            Integer bidRecordId,
            String username,
            String authorization,
            List<UploadItem> files) {
        BidderAccount account = authenticate(authorization);
        if (username == null || !username.equals(account.getUsername())) {
            throw new AppException(
                    AppErrorCode.COMMON_INVALID_ARGUMENT,
                    "bidder",
                    "UPLOAD_CREDENTIAL_FILES",
                    "VERIFY_USERNAME",
                    "上传账号与当前登录账号不一致");
        }
        return withAccountContext(account, () -> {
            requireOwned(account, bidRecordId);
            return fileUploadService.upload(
                    bidRecordId,
                    account.getBidderAccountId(),
                    account.getUsername(),
                    files);
        });
    }

    /** 删除本人投标记录下的一条资信文件及其派生资源。 */
    public void deleteCredentialFile(
            Integer bidRecordId,
            Long fileId,
            String authorization) {
        BidderAccount account = authenticate(authorization);
        withAccountContext(account, () -> {
            requireOwned(account, bidRecordId);
            fileUploadService.delete(bidRecordId, fileId);
            return null;
        });
    }

    /** 查询本人投标记录下的资信文件状态。 */
    public List<CredentialFile> listCredentialFiles(
            Integer bidRecordId,
            String authorization) {
        requireOwned(authenticate(authorization), bidRecordId);
        return fileUploadService.listByBidRecordId(bidRecordId);
    }

    /** 订阅本人投标记录下的资信文件状态事件。 */
    public SseEmitter subscribeCredentialFileEvents(
            Integer bidRecordId,
            String authorization) {
        requireOwned(authenticate(authorization), bidRecordId);
        return fileStatusEventPublisher.subscribe(
                FileStatusEventPublisher.CREDENTIAL_TOPIC_PREFIX + bidRecordId);
    }

    /** 校验下载归属后打开资信原始对象。 */
    public FileDownloadService.DownloadResource openCredentialOriginal(
            Integer bidRecordId,
            Long fileId,
            String authorization) {
        requireOwnedForDownload(authenticate(authorization), bidRecordId);
        return fileDownloadService.openCredentialOriginal(fileId, bidRecordId);
    }

    /** 校验下载归属后打开资信 Markdown 对象。 */
    public FileDownloadService.DownloadResource openCredentialMarkdown(
            Integer bidRecordId,
            Long fileId,
            String authorization) {
        requireOwnedForDownload(authenticate(authorization), bidRecordId);
        return fileDownloadService.openCredentialMarkdown(fileId, bidRecordId);
    }

    private BidderAccount authenticate(String authorization) {
        return bidderAccountService.authenticateBasic(authorization);
    }

    private BidRecord requireOwned(
            BidderAccount account,
            Integer bidRecordId) {
        BidRecord record = bidRecordService.getById(bidRecordId);
        if (record == null) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_NOT_FOUND,
                    "bid-record",
                    "BIDDER_ACCESS",
                    "LOAD_RECORD",
                    "投标记录不存在: " + bidRecordId);
        }
        if (!ownedBy(record, account)) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_FORBIDDEN,
                    "bid-record",
                    "BIDDER_ACCESS",
                    "CHECK_OWNERSHIP",
                    "无权操作该投标记录");
        }
        return record;
    }

    private void requireOwnedForDownload(
            BidderAccount account,
            Integer bidRecordId) {
        BidRecord record = bidRecordService.getById(bidRecordId);
        if (record == null) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_NOT_FOUND,
                    "bid-record",
                    "DOWNLOAD",
                    "LOAD_RECORD",
                    "投标记录不存在");
        }
        if (!ownedBy(record, account)) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_FORBIDDEN,
                    "bid-record",
                    "DOWNLOAD",
                    "CHECK_OWNERSHIP",
                    "无权下载该投标记录的资信文件");
        }
    }

    private boolean ownedBy(BidRecord record, BidderAccount account) {
        return record.getBidderAccountId() != null
                && record.getBidderAccountId().equals(
                        account.getBidderAccountId());
    }

    private <T> T withAccountContext(
            BidderAccount account,
            Supplier<T> action) {
        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BIDDER,
                account.getBidderAccountId(),
                account.getUsername()));
        try {
            return action.get();
        } finally {
            CurrentAccountContext.clear();
        }
    }
}
