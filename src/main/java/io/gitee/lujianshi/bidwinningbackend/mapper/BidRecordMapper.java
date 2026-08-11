package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投标记录 Mapper。继承 {@link BaseMapper}，绑定 {@code BidRecordMapper.xml}（V18 重命名自 TendererDetailMapper）。
 *
 * <p>V18 新增 2 个业务方法：</p>
 * <ul>
 *   <li>{@link #selectMaxBidNo} — 计算同一公告下同一投标人的当前最大 bidNo，用于服务层生成下一号</li>
 *   <li>{@link #selectByInvitationId} — 按公告 ID 查询所有投标记录（前端"查看投标人"场景）</li>
 * </ul>
 */
@Mapper
public interface BidRecordMapper extends BaseMapper<BidRecord, Integer> {

    /**
     * 计算指定 (公告, 投标人) 组合的当前最大 bidNo。
     *
     * @param invitationId 招标公告 ID
     * @param bidderName   投标人名称
     * @return 最大 bidNo；无历史投标时返回 null（Service 负责转 0 → 1）
     */
    Integer selectMaxBidNo(@Param("invitationId") Integer invitationId,
                           @Param("bidderName") String bidderName);

    /**
     * 按招标公告 ID 查询所有投标记录，按 bidAt 升序。
     *
     * @param invitationId 招标公告 ID
     * @return 投标记录列表；无记录时返回空列表
     */
    List<BidRecord> selectByInvitationId(@Param("invitationId") Integer invitationId);

    /**
     * 按投标方账号 ID 查询其全部投标记录（"我的投标"），按 bidAt 倒序。
     *
     * @param bidderAccountId 投标方账号 ID
     * @return 该账号的投标记录；无记录时返回空列表
     */
    List<BidRecord> selectByBidderAccountId(
            @Param("bidderAccountId") Long bidderAccountId);

    /** 抢占结构化投标记录的向量化权。 */
    int markVectorizing(@Param("bidRecordId") Integer bidRecordId);

    /** 结构化投标记录向量化完成，实际文档数必须为 1。 */
    int markVectorized(
            @Param("bidRecordId") Integer bidRecordId,
            @Param("chunkCount") int chunkCount,
            @Param("indexedAt") LocalDateTime indexedAt);

    /** 结构化投标记录向量化失败并保留可重试状态。 */
    int markVectorizeFailed(
            @Param("bidRecordId") Integer bidRecordId,
            @Param("errorMessage") String errorMessage);

    /** 抢占投标记录跨资源删除权；失败或超时中断的删除可幂等续作。 */
    int beginDeletion(
            @Param("bidRecordId") Integer bidRecordId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("staleBefore") LocalDateTime staleBefore);

    /** 保留删除意图和失败点，供重试或重启恢复继续收敛。 */
    int markDeletionFailed(
            @Param("bidRecordId") Integer bidRecordId,
            @Param("failedStage") String failedStage,
            @Param("errorMessage") String errorMessage);

    /** JVM 启动时把上一次进程留下的 DELETING 转成可重新抢占的 FAILED。 */
    int markInterruptedDeletionsFailed(@Param("errorMessage") String errorMessage);

    /** 在事务中锁定指定投标记录，供资信路径汇总的读改写操作使用。 */
    BidRecord selectByIdForUpdate(@Param("bidRecordId") Integer bidRecordId);

    /** 原子抢占投标记录的风险分析权。 */
    int claimRiskAnalysis(
            @Param("bidRecordId") Integer bidRecordId,
            @Param("startedAt") LocalDateTime startedAt);

    /** 将当前风险分析标记为完成。 */
    int markRiskAnalysisCompleted(@Param("bidRecordId") Integer bidRecordId);

    /** 将当前风险分析标记为失败并记录原因。 */
    int markRiskAnalysisFailed(
            @Param("bidRecordId") Integer bidRecordId,
            @Param("errorMessage") String errorMessage);

    /** JVM 启动时收敛上一次进程中断的风险分析。 */
    int markInterruptedRiskAnalysesFailed(
            @Param("errorMessage") String errorMessage);
}
