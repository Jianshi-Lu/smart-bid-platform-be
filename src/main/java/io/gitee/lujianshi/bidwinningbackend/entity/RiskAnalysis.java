package io.gitee.lujianshi.bidwinningbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 风险分析结果 — 数据库实体。对应表 {@code risk_analysis}（V7 建表，V8 重命名，V9 精简，V16 列重命名）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("risk_analysis")
public class RiskAnalysis {

    @TableId(type = IdType.AUTO)
    private Integer analysisId;

    private Integer tendererId;

    private String tendererName;

    private String riskLevel;

    private String summary;

    private String suggestions;

    private String rawResponse;

    private String promptVersion;

    private LocalDateTime analyzedAt;
}
