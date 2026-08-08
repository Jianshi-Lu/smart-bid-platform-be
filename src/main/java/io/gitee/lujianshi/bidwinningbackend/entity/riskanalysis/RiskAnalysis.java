package io.gitee.lujianshi.bidwinningbackend.entity.riskanalysis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 风险分析结果 — 数据库实体。对应表 {@code risk_analysis}（V7 建表，V8 重命名，V9 精简，V16 列重命名，V18 列再重命名）。
 *
 * <p>V18 字段变化：</p>
 * <ul>
 *   <li>{@code tendererId} → {@code bidRecordId}（逻辑外键 → {@code bid_record.bid_record_id}）</li>
 *   <li>{@code tendererName} → {@code bidderName}</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("risk_analysis")
public class RiskAnalysis {

    @TableId(type = IdType.AUTO)
    private Integer analysisId;

    private Integer bidRecordId;

    private String bidderName;

    private String riskLevel;

    private String summary;

    private String suggestions;

    private String rawResponse;

    private String promptVersion;

    private LocalDateTime analyzedAt;
}
