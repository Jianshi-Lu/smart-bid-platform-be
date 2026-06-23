package io.gitee.lujianshi.bidwinningbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidWinningRiskAnalysisMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风险分析结果 — 实体
 *
 * <p>对应表 {@code bid_winning_risk_analysis}（V7 建表）。
 * 存储 AI 模型对中标供应商的风险分析结果，每次分析产生一条记录，
 * 同一 {@code detailId} 可有多条记录用于历史追溯。</p>
 *
 * <h3>字段说明</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>analysisId</td><td>Integer</td><td>主键（自增）</td></tr>
 *   <tr><td>detailId</td><td>Integer</td><td>逻辑外键，关联 bid_winning_supplier_detail.detail_id</td></tr>
 *   <tr><td>supplierName</td><td>String</td><td>供应商名称（冗余存储）</td></tr>
 *   <tr><td>notificationId</td><td>Integer</td><td>逻辑外键，关联 bid_winning_notification.notification_id</td></tr>
 *   <tr><td>riskLevel</td><td>String</td><td>风险等级：LOW / MEDIUM / HIGH / CRITICAL</td></tr>
 *   <tr><td>riskScore</td><td>BigDecimal</td><td>风险评分 0.00-1.00</td></tr>
 *   <tr><td>summary</td><td>String</td><td>风险摘要（模型输出）</td></tr>
 *   <tr><td>keyPoints</td><td>String</td><td>关键风险点 JSON 数组</td></tr>
 *   <tr><td>suggestions</td><td>String</td><td>建议措施</td></tr>
 *   <tr><td>rawResponse</td><td>String</td><td>模型原始响应（便于审计/重放）</td></tr>
 *   <tr><td>modelName</td><td>String</td><td>模型名称，如 deepseek-chat</td></tr>
 *   <tr><td>promptVersion</td><td>String</td><td>prompt 模板版本号</td></tr>
 *   <tr><td>analyzedAt</td><td>LocalDateTime</td><td>分析时间（DB 默认 CURRENT_TIMESTAMP）</td></tr>
 * </table>
 *
 * @see BidWinningRiskAnalysisMapper
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid_winning_risk_analysis")
public class BidWinningRiskAnalysis {

    @TableId(type = IdType.AUTO)
    private Integer analysisId;

    private Integer detailId;

    private String supplierName;

    private Integer notificationId;

    private String riskLevel;

    private BigDecimal riskScore;

    private String summary;

    private String keyPoints;

    private String suggestions;

    private String rawResponse;

    private String modelName;

    private String promptVersion;

    private LocalDateTime analyzedAt;
}
