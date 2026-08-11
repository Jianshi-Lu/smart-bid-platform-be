package io.gitee.lujianshi.bidwinningbackend.service.bidrecord;

import io.gitee.lujianshi.bidwinningbackend.storage.vector.BidRecordVectorStorage;
import io.gitee.lujianshi.bidwinningbackend.storage.vector.model.PreparedBidRecordDocument;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.InvitationForBidMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/** 结构化投标记录的构造、替换写入、状态收敛与删除边界。 */
@Service
public class BidRecordVectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(
            BidRecordVectorIndexService.class);

    @Resource
    private BidRecordMapper bidRecordMapper;

    @Resource
    private InvitationForBidMapper invitationForBidMapper;

    @Resource
    private BidRecordVectorStorage vectorStorage;

    /** 多文件后台转换可能同时刷新同一记录；单 JVM Demo 用记录级锁串行替换。 */
    private final ConcurrentMap<Integer, ReentrantLock> recordLocks =
            new ConcurrentHashMap<>();

    /** 重新读取数据库事实并以稳定 ID 替换一条结构化投标记录。 */
    public BidRecord reindex(Integer bidRecordId) {
        ReentrantLock lock = lockFor(bidRecordId);
        lock.lock();
        try {
            return reindexLocked(bidRecordId);
        } finally {
            lock.unlock();
        }
    }

    private BidRecord reindexLocked(Integer bidRecordId) {
        BidRecord record = requireRecord(bidRecordId);
        InvitationForBid invitation = invitationForBidMapper.selectById(
                record.getInvitationId());
        if (invitation == null) {
            throw failure(
                    "LOAD_INVITATION",
                    "投标记录关联的招标公告不存在: " + record.getInvitationId(),
                    null);
        }
        if (bidRecordMapper.markVectorizing(bidRecordId) != 1) {
            throw failure(
                    "CLAIM_VECTORIZE",
                    "投标记录无法进入向量化状态: " + bidRecordId,
                    null);
        }

        try {
            int chunkCount = vectorStorage.replaceDocument(
                    prepare(record, invitation));
            if (chunkCount != 1 || bidRecordMapper.markVectorized(
                    bidRecordId, chunkCount, LocalDateTime.now()) != 1) {
                vectorStorage.deleteDocument(bidRecordId);
                throw failure(
                        "MARK_VECTORIZED",
                        "投标记录向量状态更新失败: " + bidRecordId,
                        null);
            }
            return requireRecord(bidRecordId);
        } catch (RuntimeException failure) {
            try {
                vectorStorage.deleteDocument(bidRecordId);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            int rows = bidRecordMapper.markVectorizeFailed(
                    bidRecordId, errorMessage(failure));
            if (rows != 1) {
                failure.addSuppressed(failure(
                        "MARK_VECTORIZE_FAILED",
                        "投标记录向量失败状态更新失败: " + bidRecordId,
                        null));
            }
            throw failure;
        }
    }

    /** 重新索引某公告下的全部投标记录，供公告信息修改后刷新派生文档。 */
    public void reindexByInvitationId(Integer invitationId) {
        List<BidRecord> records = bidRecordMapper.selectByInvitationId(invitationId);
        if (records == null) {
            return;
        }
        for (BidRecord record : records) {
            if (record != null && record.getBidRecordId() != null) {
                try {
                    reindex(record.getBidRecordId());
                } catch (RuntimeException failure) {
                    // 公告事实已经提交。逐记录继续刷新，失败行由 reindex 标记 FAILED，
                    // 不能因第一条派生索引失败而让后续记录全部保留旧公告内容。
                    log.error(
                            "公告关联投标记录索引刷新失败: invitationId={}, bidRecordId={}",
                            invitationId,
                            record.getBidRecordId(),
                            failure);
                }
            }
        }
    }

    /** 删除结构化记录与其全部资信 chunks，并读后验证为零。 */
    public void deleteAllMaterials(Integer bidRecordId) {
        ReentrantLock lock = lockFor(bidRecordId);
        lock.lock();
        try {
            vectorStorage.deleteAllMaterials(bidRecordId);
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock lockFor(Integer bidRecordId) {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw failure("VALIDATE_RECORD_ID", "投标记录 ID 必须是正整数", null);
        }
        return recordLocks.computeIfAbsent(
                bidRecordId, ignored -> new ReentrantLock());
    }

    private BidRecord requireRecord(Integer bidRecordId) {
        BidRecord record = bidRecordId == null
                ? null
                : bidRecordMapper.selectById(bidRecordId);
        if (record == null) {
            throw new AppException(
                    AppErrorCode.BID_RECORD_NOT_FOUND,
                    "bid-record",
                    "VECTORIZE",
                    "LOAD_RECORD",
                    "投标记录不存在: " + bidRecordId);
        }
        return record;
    }

    private PreparedBidRecordDocument prepare(
            BidRecord record,
            InvitationForBid invitation) {
        return new PreparedBidRecordDocument(
                record.getBidRecordId(),
                structuredContent(record, invitation),
                record.getInvitationId(),
                record.getBidderName(),
                record.getBidNo(),
                record.getBidderAccountId(),
                invitation.getProjectNo(),
                invitation.getPurchaseTaskNo(),
                record.getBidAt() == null ? null : record.getBidAt().toString(),
                record.getQuotePrice() == null
                        ? null
                        : record.getQuotePrice().toPlainString());
    }

    /** 所有投标与公告字段都显式写入正文；null 也保留字段名，避免信息边界含糊。 */
    private String structuredContent(
            BidRecord record,
            InvitationForBid invitation) {
        StringBuilder content = new StringBuilder(1024)
                .append("# 投标记录\n\n")
                .append("- 投标记录 ID：").append(value(record.getBidRecordId())).append('\n')
                .append("- 招标公告 ID：").append(value(record.getInvitationId())).append('\n')
                .append("- 投标方账号 ID：").append(value(record.getBidderAccountId())).append('\n')
                .append("- 投标人名称：").append(value(record.getBidderName())).append('\n')
                .append("- 投标次序：").append(value(record.getBidNo())).append('\n')
                .append("- 投标时间：").append(value(record.getBidAt())).append('\n')
                .append("- 报价：").append(value(record.getQuotePrice())).append('\n')
                .append("- 联系方式：").append(value(record.getBidderContact())).append('\n')
                .append("- 风险分析状态：").append(value(record.getRiskAnalysisStatus())).append('\n')
                .append("- 风险分析开始时间：").append(value(record.getRiskAnalysisStartedAt())).append('\n')
                .append("- 风险分析错误：").append(value(record.getRiskAnalysisError())).append("\n\n")
                .append("## 资信材料对象路径\n\n");
        List<String> credentials = record.getCredentials();
        if (credentials == null || credentials.isEmpty()) {
            content.append("- 无\n");
        } else {
            credentials.forEach(path -> content.append("- ").append(value(path)).append('\n'));
        }
        return content.append("\n## 招标公告\n\n")
                .append("- 经办人 ID：").append(value(invitation.getBidHandlerId())).append('\n')
                .append("- 项目编号：").append(value(invitation.getProjectNo())).append('\n')
                .append("- 采购任务编号：").append(value(invitation.getPurchaseTaskNo())).append('\n')
                .append("- 平台类别：").append(value(invitation.getPlatformCategory())).append('\n')
                .append("- 招标流程：").append(value(invitation.getTenderProcess())).append('\n')
                .append("- 招标方式：").append(value(invitation.getTenderMethod())).append('\n')
                .append("- 采购方式：").append(value(invitation.getPurchaseMethod())).append('\n')
                .append("- 是否固定价格：").append(value(invitation.getIsFixedPrice())).append('\n')
                .append("- 是否收取文件费：").append(value(invitation.getHasDocumentFee())).append('\n')
                .append("- 保证金：").append(value(invitation.getDeposit())).append('\n')
                .append("- 是否要求信用报告：").append(value(invitation.getCreditReportRequired())).append('\n')
                .append("- 报名截止时间：").append(value(invitation.getRegistrationDeadline())).append('\n')
                .append("- 技术标截止时间：").append(value(invitation.getTechnicalTenderDeadline())).append('\n')
                .append("- 商务标截止时间：").append(value(invitation.getBusinessTenderDeadline())).append('\n')
                .append("- 备注：").append(value(invitation.getNotes())).append('\n')
                .append("- 公告创建时间：").append(value(invitation.getCreatedAt())).append('\n')
                .append("- 采购公司：").append(value(invitation.getPurchaseCompany())).append('\n')
                .append("- 采购部门：").append(value(invitation.getPurchaseDepartment())).append('\n')
                .append("- 招标方：").append(value(invitation.getTenderingParty())).append('\n')
                .append("- 公告状态：").append(value(invitation.getStatus())).append('\n')
                .toString();
    }

    private String value(Object value) {
        return value == null ? "未填写" : String.valueOf(value);
    }

    private String errorMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure == null ? "投标记录向量化失败"
                    : failure.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private AppException failure(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.BID_RECORD_VECTORIZATION_FAILED,
                "bid-record",
                "VECTORIZE",
                stage,
                message,
                cause);
    }
}
