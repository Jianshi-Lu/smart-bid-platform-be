package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningRiskAnalysis;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 风险分析结果 Mapper — 继承 {@link BaseMapper}，提供标准 CRUD + 按 detailId 查询。
 *
 * <h3>继承的方法</h3>
 * <ul>
 *   <li>{@code int insert(BidWinningRiskAnalysis entity)} — 插入分析记录（自增 analysisId 回填）</li>
 *   <li>{@code BidWinningRiskAnalysis selectById(Integer id)} — 按 analysisId 查询</li>
 *   <li>{@code List&lt;BidWinningRiskAnalysis&gt; selectAll()} — 查询全部</li>
 *   <li>{@code int deleteById(Integer id)} — 按 analysisId 删除</li>
 *   <li>{@code int updateById(BidWinningRiskAnalysis entity)} — 按 analysisId 更新</li>
 *   <li>{@code List&lt;BidWinningRiskAnalysis&gt; selectByIds(List&lt;Integer&gt; ids)} — 按 ID 列表批量查询</li>
 *   <li>{@code int deleteByIds(List&lt;Integer&gt; ids)} — 按 ID 列表批量删除</li>
 * </ul>
 *
 * <h3>本接口新增方法</h3>
 * <ul>
 *   <li>{@link #selectByDetailId(Integer)} — 按 detailId 查询历史分析记录（按时间倒序）</li>
 * </ul>
 *
 * @see BidWinningRiskAnalysis
 * @see io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper
 */
@Mapper
public interface BidWinningRiskAnalysisMapper extends BaseMapper<BidWinningRiskAnalysis> {

    /**
     * 按中标明细 ID 查询历史分析记录。
     *
     * <p>同一 {@code detailId} 可有多条分析记录，按 {@code analyzed_at DESC} 排序，
     * 第一条为最新分析结果。</p>
     *
     * @param detailId 中标明细 ID
     * @return 分析记录列表（按时间倒序），无记录时返回空列表
     */
    List<BidWinningRiskAnalysis> selectByDetailId(Integer detailId);
}
