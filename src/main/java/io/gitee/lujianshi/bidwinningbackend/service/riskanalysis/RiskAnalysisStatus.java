package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

/** 投标记录当前风险分析任务状态。 */
public enum RiskAnalysisStatus {
    /** 尚未分析，或允许再次发起分析。 */
    IDLE,

    /** 当前已有一个分析请求正在执行。 */
    ANALYZING,

    /** 最近一次分析成功完成。 */
    COMPLETED,

    /** 最近一次分析失败，允许重试。 */
    FAILED
}
