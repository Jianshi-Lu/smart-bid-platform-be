package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

/** 当前投标记录已经存在正在执行的风险分析请求。 */
public class RiskAnalysisInProgressException extends RuntimeException {

    public RiskAnalysisInProgressException(Integer bidRecordId) {
        super("该投标记录正在进行风险分析，请稍后再试: " + bidRecordId);
    }
}
