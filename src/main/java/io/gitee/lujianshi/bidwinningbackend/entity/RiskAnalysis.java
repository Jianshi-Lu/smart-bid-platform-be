package io.gitee.lujianshi.bidwinningbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 风险分析结果 — 数据库实体。对应表 {@code risk_analysis}（V7 建表，V8 重命名，V9 精简 9 字段）。
 *
 * <h3>字段 ↔ 列映射</h3>
 * <table border="1">
 *   <tr><th>Java 字段</th><th>DB 列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>analysisId</td><td>analysis_id</td><td>INT, AUTO_INCREMENT, PK</td><td>主键</td></tr>
 *   <tr><td>supplierId</td><td>supplier_id</td><td>INT, NOT NULL</td><td>逻辑外键，关联 supplier_detail.supplier_id</td></tr>
 *   <tr><td>supplierName</td><td>supplier_name</td><td>VARCHAR(255), NOT NULL</td><td>供应商名称（冗余）</td></tr>
 *   <tr><td>riskLevel</td><td>risk_level</td><td>VARCHAR(20)</td><td>LOW / MEDIUM / HIGH / CRITICAL</td></tr>
 *   <tr><td>summary</td><td>summary</td><td>TEXT</td><td>风险摘要</td></tr>
 *   <tr><td>suggestions</td><td>suggestions</td><td>TEXT</td><td>建议措施</td></tr>
 *   <tr><td>rawResponse</td><td>raw_response</td><td>JSON (V11)</td><td>模型原始响应 JSON</td></tr>
 *   <tr><td>promptVersion</td><td>prompt_version</td><td>VARCHAR(20)</td><td>prompt 模板版本</td></tr>
 *   <tr><td>analyzedAt</td><td>analyzed_at</td><td>DATETIME</td><td>分析时间（DB 默认 CURRENT_TIMESTAMP）</td></tr>
 * </table>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("risk_analysis")
public class RiskAnalysis {

    @TableId(type = IdType.AUTO)
    private Integer analysisId;

    private Integer supplierId;

    private String supplierName;

    private String riskLevel;

    private String summary;

    private String suggestions;

    private String rawResponse;

    private String promptVersion;

    private LocalDateTime analyzedAt;
}
