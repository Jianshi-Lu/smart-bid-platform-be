package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidRecord;
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
}
