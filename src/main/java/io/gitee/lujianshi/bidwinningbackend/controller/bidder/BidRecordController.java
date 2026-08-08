package io.gitee.lujianshi.bidwinningbackend.controller.bidder;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.dto.bidrecord.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidrecord.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.BidRecordService;
import io.gitee.lujianshi.bidwinningbackend.service.BidderAccountService;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 投标方自己的投标记录管理入口（登录后）。
 *
 * <p>每个接口先通过 {@link BidderAccountService#authenticateBasic} 校验
 * HTTP Basic 凭据得到当前账号，再校验账号是否拥有对应记录：
 * 未绑定账号的存量记录/招标方代录记录，投标方一律不可见、不可操作。
 * 上传资信文件同样先校验记录归属，保证文件链路落到账号可溯源的记录上。</p>
 *
 * @since V36
 */
@RestController("bidderBidRecordController")
@RequestMapping("/bidder/bid-records")
public class BidRecordController {

    @Resource
    private BidRecordService bidRecordService;

    @Resource
    private FileUploadService fileUploadService;

    @Resource
    private BidderAccountService bidderAccountService;

    @Resource
    private FileStatusEventPublisher fileStatusEventPublisher;

    /** 当前账号的投标记录列表（按 bidAt 倒序）。 */
    @GetMapping
    public Result<List<BidRecord>> myBidRecords(HttpServletRequest request) {
        BidderAccount account = currentAccount(request);
        return Result.success(
                bidRecordService.listByBidderAccountId(
                        account.getBidderAccountId()),
                "查询成功");
    }

    /** 投标：创建投标记录并绑定当前账号。 */
    @PostMapping
    public Result<BidRecord> create(
            @Valid @RequestBody AddRequest request,
            HttpServletRequest httpRequest) {
        BidderAccount account = currentAccount(httpRequest);
        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BIDDER,
                account.getBidderAccountId(),
                account.getUsername()));
        try {
            BidRecord entity = new BidRecord();
            entity.setInvitationId(request.getInvitationId());
            entity.setBidderName(request.getBidderName());
            entity.setBidderAccountId(account.getBidderAccountId());
            entity.setQuotePrice(request.getQuotePrice());
            entity.setBidderContact(request.getBidderContact());
            entity.setCredentials(request.getCredentials());
            bidRecordService.add(entity);
            return Result.success(entity, "投标成功");
        } finally {
            CurrentAccountContext.clear();
        }
    }

    /** 查询自己的投标记录详情。 */
    @GetMapping("/{id}")
    public Result<BidRecord> get(
            @PathVariable Integer id,
            HttpServletRequest request) {
        return Result.success(authenticate(request, id), "查询成功");
    }

    /**
     * 修改自己的投标记录（PATCH 语义）。
     *
     * <p>归属与转换产物路径不可修改：{@code credentials} 由转换链路维护，
     * 客户端传入也会被忽略。</p>
     */
    @PatchMapping("/{id}")
    public Result<Diff<BidRecord>> update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRequest request,
            HttpServletRequest httpRequest) {
        BidderAccount account = currentAccount(httpRequest);
        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BIDDER,
                account.getBidderAccountId(),
                account.getUsername()));
        try {
            BidRecord before = requireOwned(account, id);

            BidRecord after = new BidRecord();
            after.setBidRecordId(id);
            after.setBidderName(request.getBidderName());
            after.setQuotePrice(request.getQuotePrice());
            after.setBidderContact(request.getBidderContact());
            // 投标方不能改 credentials / invitationId / bidNo / bidAt / 账号归属。
            int rows = bidRecordService.update(after);
            if (rows == 0) {
                throw bidRecordNotFound(id);
            }
            return Result.success(
                    new Diff<>(before, bidRecordService.getById(id)), "修改成功");
        } finally {
            CurrentAccountContext.clear();
        }
    }

    /** 删除自己的投标记录（物理删除，不级联资信文件）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable Integer id,
            HttpServletRequest request) {
        BidderAccount account = currentAccount(request);
        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BIDDER,
                account.getBidderAccountId(),
                account.getUsername()));
        try {
            requireOwned(account, id);
            int rows = bidRecordService.delete(id);
            if (rows == 0) {
                throw bidRecordNotFound(id);
            }
            return Result.success(null, "删除成功");
        } finally {
            CurrentAccountContext.clear();
        }
    }

    /** 向自己的投标记录上传资信材料。 */
    @PostMapping(value = "/{id}/credential-files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadResponse> uploadCredentialFiles(
            @PathVariable Integer id,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("username") String username,
            HttpServletRequest request) {
        BidderAccount account = currentAccount(request);
        if (username == null || !username.equals(account.getUsername())) {
            throw new AppException(
                    AppErrorCode.COMMON_INVALID_ARGUMENT,
                    "bidder",
                    "UPLOAD_CREDENTIAL_FILES",
                    "VERIFY_USERNAME",
                    "上传账号与当前登录账号不一致");
        }
        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BIDDER,
                account.getBidderAccountId(),
                account.getUsername()));
        try {
            requireOwned(account, id);
            return Result.success(
                    fileUploadService.upload(
                            id,
                            account.getBidderAccountId(),
                            account.getUsername(),
                            toUploadItems(files)),
                    "上传成功");
        } finally {
            CurrentAccountContext.clear();
        }
    }

    /** 删除自己的投标记录下的一个资信文件（连同存储对象与 Markdown 投影）。 */
    @DeleteMapping("/{id}/credential-files/{fileId}")
    public Result<Void> deleteCredentialFile(
            @PathVariable Integer id,
            @PathVariable Long fileId,
            HttpServletRequest request) {
        BidderAccount account = currentAccount(request);
        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BIDDER,
                account.getBidderAccountId(),
                account.getUsername()));
        try {
            requireOwned(account, id);
            fileUploadService.delete(id, fileId);
            return Result.success(null, "删除成功");
        } finally {
            CurrentAccountContext.clear();
        }
    }

    /** 查询自己的投标记录下的全部资信文件（含转换状态，已删除文件不可见）。 */
    @GetMapping("/{id}/credential-files")
    public Result<List<CredentialFile>> listCredentialFiles(
            @PathVariable Integer id,
            HttpServletRequest request) {
        authenticate(request, id);
        return Result.success(
                fileUploadService.listByBidRecordId(id), "查询成功");
    }

    /** 订阅自己的投标记录下资信文件转换状态变化。 */
    @GetMapping(
            value = "/{id}/credential-files/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter credentialFileEvents(
            @PathVariable Integer id,
            HttpServletRequest request) {
        BidderAccount account = currentAccount(request);
        requireOwned(account, id);
        return fileStatusEventPublisher.subscribe(
                FileStatusEventPublisher.CREDENTIAL_TOPIC_PREFIX + id);
    }

    private BidderAccount currentAccount(HttpServletRequest request) {
        return bidderAccountService.authenticateBasic(
                request.getHeader("Authorization"));
    }

    /**
     * 投标方按 id 操作本人记录的统一入口：先校验 Basic 凭据（401），
     * 再校验账号拥有该投标记录（404/403），两步绑定防止新增接口漏校验。
     *
     * @param request 携带 Basic 凭据的 HTTP 请求
     * @param bidRecordId 目标投标记录 ID
     * @return 属于当前账号的投标记录
     * @throws AppException 401 鉴权失败 / 403 无归属权 / 404 记录不存在
     */
    private BidRecord authenticate(
            HttpServletRequest request,
            Integer bidRecordId) {
        return requireOwned(currentAccount(request), bidRecordId);
    }

    private BidRecord requireOwned(
            BidderAccount account,
            Integer bidRecordId) {
        BidRecord record = bidRecordService.getById(bidRecordId);
        if (record == null) {
            throw bidRecordNotFound(bidRecordId);
        }
        if (record.getBidderAccountId() == null
                || !record.getBidderAccountId()
                        .equals(account.getBidderAccountId())) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_FORBIDDEN,
                    "bid-record",
                    "BIDDER_ACCESS",
                    "CHECK_OWNERSHIP",
                    "无权操作该投标记录");
        }
        return record;
    }

    private AppException bidRecordNotFound(Integer bidRecordId) {
        return new AppException(
                AppErrorCode.BID_RECORD_NOT_FOUND,
                "bid-record",
                "BIDDER_ACCESS",
                "LOAD_RECORD",
                "投标记录不存在: " + bidRecordId);
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
