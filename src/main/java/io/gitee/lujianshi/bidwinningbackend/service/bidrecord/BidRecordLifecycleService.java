package io.gitee.lujianshi.bidwinningbackend.service.bidrecord;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.enums.BidNoticeStatus;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidderAccountMapper;
import io.gitee.lujianshi.bidwinningbackend.service.InvitationForBidService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 所有投标记录写入口共用的数据库、对象存储和私有向量生命周期。 */
@Service
public class BidRecordLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(
            BidRecordLifecycleService.class);
    private static final long DELETE_STALE_SECONDS = 300;

    @Resource
    private BidRecordService bidRecordService;

    @Resource
    private BidRecordVectorIndexService vectorIndexService;

    @Resource
    private BidRecordMapper bidRecordMapper;

    @Resource
    private FileUploadService fileUploadService;

    @Resource
    private BidderAccountMapper bidderAccountMapper;

    @Resource
    private InvitationForBidService invitationForBidService;

    /** 创建业务事实后立即尝试派生索引；索引失败保留 FAILED 锚点供恢复重建。 */
    public BidRecord create(BidRecord entity) {
        requireBiddingOpen(entity);
        if (bidRecordService.add(entity) != 1 || entity.getBidRecordId() == null) {
            throw persistenceFailure("CREATE_RECORD", "投标记录创建失败", null);
        }
        return reindexBestEffort(entity.getBidRecordId(), "CREATE_RECORD");
    }

    /** 更新数据库事实后立即尝试替换结构化向量；索引故障不伪装成业务更新失败。 */
    public BidRecord update(BidRecord patch) {
        if (patch == null || patch.getBidRecordId() == null) {
            throw persistenceFailure("VALIDATE_UPDATE", "投标记录更新参数不正确", null);
        }
        if (bidRecordService.update(patch) != 1) {
            throw notFound(patch.getBidRecordId(), "UPDATE_RECORD");
        }
        return reindexBestEffort(patch.getBidRecordId(), "UPDATE_RECORD");
    }

    /** 先持久化删除意图，再清理全部派生资源，最后物理删除父记录。 */
    public BidRecord delete(Integer bidRecordId, String knownUsername) {
        BidRecord existing = bidRecordService.getById(bidRecordId);
        if (existing == null) {
            throw notFound(bidRecordId, "LOAD_RECORD");
        }
        LocalDateTime startedAt = LocalDateTime.now();
        if (bidRecordMapper.beginDeletion(
                bidRecordId,
                startedAt,
                startedAt.minusSeconds(DELETE_STALE_SECONDS)) != 1) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_DELETE_IN_PROGRESS,
                    "bid-record",
                    "DELETE",
                    "CLAIM_DELETE",
                    "投标记录正在删除中，请稍后重试: " + bidRecordId);
        }

        try {
            String username = resolveUsername(existing, knownUsername);
            fileUploadService.deleteAllForBidRecord(bidRecordId, username);
            vectorIndexService.deleteAllMaterials(bidRecordId);
            if (bidRecordService.delete(bidRecordId) != 1) {
                throw deleteFailure(
                        "DELETE_RECORD",
                        "投标记录删除失败: " + bidRecordId,
                        null);
            }
            return existing;
        } catch (RuntimeException failure) {
            String stage = failure instanceof AppException appException
                    ? appException.getStage()
                    : "UNKNOWN";
            try {
                if (bidRecordMapper.markDeletionFailed(
                        bidRecordId,
                        stage,
                        errorMessage(failure)) != 1) {
                    failure.addSuppressed(deleteFailure(
                            "MARK_DELETE_FAILED",
                            "投标记录删除失败状态回写失败: " + bidRecordId,
                            null));
                }
            } catch (RuntimeException stateFailure) {
                failure.addSuppressed(stateFailure);
            }
            throw failure;
        }
    }

    /** 批量删除仍逐记录走完整生命周期，不直接绕过文件和向量清理。 */
    public List<BidRecord> deleteByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<BidRecord> deleted = new ArrayList<>();
        for (Integer id : ids) {
            if (id != null && bidRecordService.getById(id) != null) {
                deleted.add(delete(id, null));
            }
        }
        return List.copyOf(deleted);
    }

    private String resolveUsername(BidRecord record, String knownUsername) {
        if (knownUsername != null && !knownUsername.isBlank()) {
            return knownUsername;
        }
        if (record.getBidderAccountId() == null) {
            return null;
        }
        BidderAccount account = bidderAccountMapper.selectById(
                record.getBidderAccountId());
        return account == null ? null : account.getUsername();
    }

    /** 后端最终约束投标窗口，避免绕过前端向草稿、终态或已截止公告投标。 */
    private void requireBiddingOpen(BidRecord record) {
        Integer invitationId = record == null ? null : record.getInvitationId();
        if (invitationId == null) {
            throw persistenceFailure("VALIDATE_INVITATION", "招标公告 ID 不能为空", null);
        }
        InvitationForBid invitation = invitationForBidService.getById(invitationId);
        if (invitation == null) {
            throw new AppException(
                    AppErrorCode.INVITATION_NOT_FOUND,
                    "bid-record",
                    "CREATE",
                    "LOAD_INVITATION",
                    "招标公告不存在: " + invitationId);
        }
        if (invitation.getStatus() != BidNoticeStatus.PUBLISHED) {
            throw biddingNotOpen(
                    "CHECK_INVITATION_STATUS",
                    "当前招标公告未处于已发布状态，不能提交投标: invitationId="
                            + invitationId);
        }
        LocalDateTime deadline = invitation.getBusinessTenderDeadline();
        if (deadline != null && !LocalDateTime.now().isBefore(deadline)) {
            throw biddingNotOpen(
                    "CHECK_BUSINESS_DEADLINE",
                    "商务投标已截止，不能继续提交投标: invitationId="
                            + invitationId);
        }
    }

    private BidRecord reindexBestEffort(Integer bidRecordId, String operation) {
        try {
            return vectorIndexService.reindex(bidRecordId);
        } catch (RuntimeException indexFailure) {
            log.error(
                    "投标记录事实已提交，但结构化索引刷新失败: operation={}, bidRecordId={}",
                    operation,
                    bidRecordId,
                    indexFailure);
            BidRecord latest = bidRecordService.getById(bidRecordId);
            if (latest == null) {
                throw persistenceFailure(
                        "RELOAD_AFTER_INDEX_FAILURE",
                        "投标记录索引失败后无法回读业务事实: " + bidRecordId,
                        indexFailure);
            }
            return latest;
        }
    }

    private String errorMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure == null ? "投标记录删除失败"
                    : failure.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private AppException notFound(Integer id, String stage) {
        return new AppException(
                AppErrorCode.BID_RECORD_NOT_FOUND,
                "bid-record",
                "LIFECYCLE",
                stage,
                "投标记录不存在: " + id);
    }

    private AppException persistenceFailure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.BID_RECORD_PERSISTENCE_FAILED,
                "bid-record",
                "LIFECYCLE",
                stage,
                message,
                cause);
    }

    private AppException biddingNotOpen(String stage, String message) {
        return new AppException(
                AppErrorCode.BID_RECORD_BIDDING_NOT_OPEN,
                "bid-record",
                "CREATE",
                stage,
                message);
    }

    private AppException deleteFailure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.BID_RECORD_DELETE_FAILED,
                "bid-record",
                "DELETE",
                stage,
                message,
                cause);
    }
}
