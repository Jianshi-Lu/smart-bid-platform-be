package io.gitee.lujianshi.bidwinningbackend.service.bidrecord;

import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.service.audit.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.service.support.BaseService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 投标记录 Service。继承 {@link BaseService}，重写 {@link #add} 加重试 + 自动 bidNo 计算，新增 {@link #listByInvitationId}（V18 重命名自 TendererDetailService）。
 *
 * <p>核心逻辑：</p>
 * <ol>
 *   <li>调用方只传 {@code invitationId} / {@code bidderName} / {@code quotePrice} / 等业务字段，</li>
 *   <li>Service 在 add 时先查当前 (invitationId, bidderName) 下的最大 bidNo，</li>
 *   <li>新 bidNo = maxBidNo + 1（首次为 1），并设 bidAt = now，</li>
 *   <li>捕获 {@link DuplicateKeyException} 重试 3 次（防并发同号）。</li>
 * </ol>
 */
@Service
public class BidRecordService extends BaseService<BidRecord> {

    /**
     * 并发同号时的最大重试次数。
     */
    private static final int MAX_RETRY = 3;

    @Resource
    private BidRecordMapper mapper;

    @Resource
    private OperationHistoryService operationHistoryService;

    @Override
    protected BaseMapper<BidRecord, Integer> getMapper() {
        return mapper;
    }

    @Override
    protected Function<BidRecord, Integer> getIdExtractor() {
        return BidRecord::getBidRecordId;
    }

    @Override
    protected void afterAdd(BidRecord entity) {
        operationHistoryService.recordBidRecord(
                entity.getBidRecordId(),
                OperationType.CREATE,
                null,
                entity,
                null);
    }

    @Override
    protected void afterUpdate(BidRecord before, BidRecord after) {
        operationHistoryService.recordBidRecord(
                after.getBidRecordId(),
                OperationType.UPDATE,
                before,
                after,
                null);
    }

    @Override
    protected void afterDelete(BidRecord deleted) {
        if (deleted == null) {
            return;
        }
        operationHistoryService.recordBidRecord(
                deleted.getBidRecordId(),
                OperationType.DELETE,
                deleted,
                null,
                null);
    }

    /**
     * 新增投标记录。自动计算 {@code bidNo}（同公告+同投标人下递增）和 {@code bidAt}（当前时间）。
     *
     * <p>并发场景：两个相同 (invitationId, bidderName) 同时进来，因 uk_attempt 唯一索引，
     * 一条插入成功，另一条抛 DuplicateKeyException；本方法捕获后重试，重试前重新查 max(bid_no)。</p>
     *
     * @param entity 待新增的实体（不含 bidNo / bidAt，Service 会自动计算）
     * @return 影响行数
     * @throws AppException 重试 3 次仍发生投标序号冲突
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int add(BidRecord entity) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            Integer maxBidNo = mapper.selectMaxBidNo(entity.getInvitationId(), entity.getBidderName());
            int nextBidNo = (maxBidNo == null) ? 1 : maxBidNo + 1;
            entity.setBidNo(nextBidNo);
            entity.setBidAt(LocalDateTime.now());

            try {
                return super.add(entity);
            } catch (DuplicateKeyException e) {
                if (attempt == MAX_RETRY) {
                    throw new AppException(
                            AppErrorCode.BID_RECORD_BID_NO_CONFLICT,
                            "bid-record",
                            "CREATE",
                            "GENERATE_BID_NO",
                            "投标序号生成发生冲突，请稍后重试",
                            e);
                }
            }
        }
        throw new AppException(
                AppErrorCode.BID_RECORD_BID_NO_CONFLICT,
                "bid-record",
                "CREATE",
                "GENERATE_BID_NO",
                "投标序号生成发生冲突");
    }

    /**
     * 按招标公告 ID 查询所有投标记录（前端"查看投标人"场景）。
     *
     * @param invitationId 招标公告 ID
     * @return 投标记录列表（按 bidAt 升序）；无记录时返回空列表
     */
    public List<BidRecord> listByInvitationId(Integer invitationId) {
        return mapper.selectByInvitationId(invitationId);
    }

    /**
     * 按投标方账号查询其全部投标记录（"我的投标"），按 bidAt 倒序。
     *
     * @param bidderAccountId 投标方账号 ID
     * @return 该账号的投标记录；无记录时返回空列表
     */
    public List<BidRecord> listByBidderAccountId(Long bidderAccountId) {
        return mapper.selectByBidderAccountId(bidderAccountId);
    }
}
