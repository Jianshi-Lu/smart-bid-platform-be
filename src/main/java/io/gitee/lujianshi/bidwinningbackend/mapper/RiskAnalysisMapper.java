package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.riskanalysis.RiskAnalysis;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 风险分析 Mapper。继承 {@link BaseMapper}，绑定 {@code RiskAnalysisMapper.xml}。
 *
 * <p>V18：{@link #selectByBidRecordId} 重命名自 selectByTendererId（FK 列 tendererId → bidRecordId）。</p>
 */
@Mapper
public interface RiskAnalysisMapper extends BaseMapper<RiskAnalysis, Integer> {

    /**
     * 按投标记录 ID 查询历史分析记录（按时间倒序）。
     *
     * @param bidRecordId 投标记录 ID
     * @return 历史分析记录列表，无记录时返回空列表
     */
    List<RiskAnalysis> selectByBidRecordId(Integer bidRecordId);
}
