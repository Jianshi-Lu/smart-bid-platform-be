package io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 更新定标通知 — 请求 DTO（Data Transfer Object）
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表
 *      ↓
 *   ② Entity
 *      ↓
 *   ③ Mapper 接口
 *      ↓
 *   ④ Mapper XML
 *      ↓
 *   ⑤ Service
 *      ↓
 *   ⑥ DTO（本类）— HTTP 请求载体
 *      ↓
 *   ⑦ Controller
 * </pre>
 *
 * <h3>PATCH 语义（RFC 5789）</h3>
 * <ul>
 *   <li>本 DTO 用于 <b>PATCH</b>（部分更新），不是 PUT（全量替换）</li>
 *   <li><b>null 字段表示"不修改"</b>（与"置空"无法区分 — 是 PATCH 标准的限制）</li>
 *   <li>需要"置空"字段的客户端场景当前<b>不支持</b>，建议加默认值/改用其他字段表达"无"</li>
 *   <li>调用方传哪些字段，就更新哪些字段；未传的字段在 DB 中保持原值</li>
 * </ul>
 *
 * <h3>字段范围（17 字段）</h3>
 * <ul>
 *   <li><b>排除</b> {@code notificationId} — 由 URL 路径 {@code @PathVariable} 传入（WHERE 条件）</li>
 *   <li><b>排除</b> {@code createdAt} — DB 层自动维护（结构上不可改）</li>
 *   <li><b>包含</b> 3 个 NOT NULL 字段 + 14 个可选字段</li>
 * </ul>
 *
 * <h3>校验规则</h3>
 * <ul>
 *   <li><b>无</b> {@code @NotBlank}/{@code @NotNull} — PATCH 语义下 null 是合法值（表示"不修改"）</li>
 *   <li>保留 {@code @Size(max=...)} 限制（确保即使是 PATCH，提供值时也不超长）</li>
 *   <li>DB 层的 NOT NULL 约束是最后防线（即使 DTO 漏校验，DB 也会拒）</li>
 * </ul>
 *
 * <h3>XML 配合（动态 SQL）</h3>
 * Mapper XML 的 {@code updateById} 用 {@code <set> + <if test="xxx != null">} 模式：
 * <pre>
 *   UPDATE bid_winning_notification
 *   &lt;set&gt;
 *     &lt;if test="handlerId != null"&gt;handler_id = #{handlerId},&lt;/if&gt;
 *     &lt;if test="projectNo != null"&gt;project_no = #{projectNo},&lt;/if&gt;
 *     ...
 *   &lt;/set&gt;
 *   WHERE notification_id = #{notificationId}
 * </pre>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification
 * @see io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BidWinningNotificationCreateRequest
 */
@Data
public class BidWinningNotificationUpdateRequest {

    // ========== 3 个 NOT NULL 字段（PATCH 可选，更新时建议提供非空值） ==========

    private Integer handlerId;

    @Size(max = 50, message = "项目编号长度不能超过50")
    private String projectNo;

    @Size(max = 50, message = "采购任务编号长度不能超过50")
    private String purchaseTaskNo;

    // ========== 14 个可选字段 ==========

    @Size(max = 50, message = "平台采购类别长度不能超过50")
    private String platformCategory;

    @Size(max = 50, message = "招标流程长度不能超过50")
    private String biddingProcess;

    @Size(max = 50, message = "招标方式长度不能超过50")
    private String biddingMethod;

    @Size(max = 50, message = "采购方式长度不能超过50")
    private String purchaseMethod;

    private Boolean isFixedPrice;

    private BigDecimal targetCost;

    private BigDecimal documentFee;

    private BigDecimal deposit;

    private Boolean creditReportRequired;

    private LocalDateTime registrationDeadline;

    private LocalDateTime techBidDeadline;

    private LocalDateTime businessBidDeadline;

    @Size(max = 20, message = "状态长度不能超过20")
    private String biddingStatus;

    private String notes;
}
