package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 风险分析 Mapper。继承 {@link BaseMapper}，绑定 {@code RiskAnalysisMapper.xml}。
 */
@Mapper
public interface RiskAnalysisMapper extends BaseMapper<RiskAnalysis> {

    /**
     * 按投标人 ID 查询历史分析记录（按时间倒序）。
     *
     * @param tendererId 投标人 ID
     * @return 历史分析记录列表，无记录时返回空列表
     */
    List<RiskAnalysis> selectByTendererId(Integer tendererId);
}
