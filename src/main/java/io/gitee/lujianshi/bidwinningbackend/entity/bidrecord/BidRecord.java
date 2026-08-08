package io.gitee.lujianshi.bidwinningbackend.entity.bidrecord;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.lujianshi.bidwinningbackend.service.riskanalysis.RiskAnalysisStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投标记录 — 数据库实体。对应表 {@code bid_record}（V18 重命名自 tenderer_detail，并合并加入
 * 招标关联 + 投标次序 + 投标时间）。
 *
 * <p>V18 字段变化：</p>
 * <ul>
 *   <li>PK：{@code tendererId} → {@code bidRecordId}</li>
 *   <li>新增 {@code invitationId}（逻辑外键 → {@code invitation_for_bid.invitation_id}）</li>
 *   <li>新增 {@code bidNo}（投标次数，同 invitationId + bidderName 下递增，从 1 开始）</li>
 *   <li>新增 {@code bidAt}（投标时间，默认 CURRENT_TIMESTAMP）</li>
 *   <li>{@code tendererName} → {@code bidderName}（World Bank 术语：Bidder）</li>
 *   <li>{@code tendererContact} → {@code bidderContact}</li>
 * </ul>
 *
 * <p>唯一索引：{@code uk_attempt (invitation_id, bidder_name, bid_no)} — 防并发同号；二级索引：{@code idx_invitation (invitation_id)} — 按公告过滤。</p>
 * <p>{@code credentials} 保存转换后 Markdown 文件路径列表，数据库中对应 JSON 数组；列表顺序与资信文件的 {@code file_order} 一致。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid_record")
public class BidRecord {

    @TableId(type = IdType.AUTO)
    private Integer bidRecordId;

    private Integer invitationId;

    private Integer bidNo;

    private LocalDateTime bidAt;

    private String bidderName;

    /** 投标方账号 ID（V36 新增，逻辑外键 → bidder_account；招标方代录或存量数据为 NULL）。 */
    private Long bidderAccountId;

    private BigDecimal quotePrice;

    private String bidderContact;

    private List<String> credentials;

    private RiskAnalysisStatus riskAnalysisStatus;

    private LocalDateTime riskAnalysisStartedAt;

    private String riskAnalysisError;
}
