package io.gitee.lujianshi.bidwinningbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.lujianshi.bidwinningbackend.mapper.RiskAnalysisMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 风险分析结果 — 实体
 *
 * <p>对应表 {@code risk_analysis}（V7 建表，V8 重命名，V9 精简字段）。
 * 存储 AI 模型对供应商的风险分析结果，每次分析产生一条记录，
 * 同一 {@code supplierId} 可有多条记录用于历史追溯。</p>
 *
 * <h3>字段说明</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>analysisId</td><td>Integer</td><td>主键（自增）</td></tr>
 *   <tr><td>supplierId</td><td>Integer</td><td>逻辑外键，关联 supplier_detail.supplier_id</td></tr>
 *   <tr><td>supplierName</td><td>String</td><td>供应商名称（冗余存储）</td></tr>
 *   <tr><td>riskLevel</td><td>String</td><td>风险等级：LOW / MEDIUM / HIGH / CRITICAL</td></tr>
 *   <tr><td>summary</td><td>String</td><td>风险摘要（模型输出）</td></tr>
 *   <tr><td>suggestions</td><td>String</td><td>建议措施</td></tr>
 *   <tr><td>rawResponse</td><td>String</td><td>模型原始响应（便于审计/重放）</td></tr>
 *   <tr><td>promptVersion</td><td>String</td><td>prompt 模板版本号</td></tr>
 *   <tr><td>analyzedAt</td><td>LocalDateTime</td><td>分析时间（DB 默认 CURRENT_TIMESTAMP）</td></tr>
 * </table>
 *
 * @see RiskAnalysisMapper
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
