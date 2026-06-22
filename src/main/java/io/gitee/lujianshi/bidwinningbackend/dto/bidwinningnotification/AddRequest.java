package io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建定标通知 — 请求 DTO（Data Transfer Object）
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表（V4 建表，bid_winning_notification）
 *      ↓
 *   ② Entity 层（BidWinningNotification，Java ↔ DB 映射）
 *      ↓
 *   ③ Mapper 接口
 *      ↓
 *   ④ Mapper XML
 *      ↓
 *   ⑤ Service
 *      ↓
 *   ⑥ DTO（本类）— HTTP 请求载体 + 校验注解
 *      ↓
 *   ⑦ Controller
 * </pre>
 *
 * <h3>字段 ↔ Entity 对应（17 字段，排除 notificationId / createdAt）</h3>
 *
 * <h3>必填字段（3 个，对应 DB 层 NOT NULL）</h3>
 * <ul>
 *   <li>{@code handlerId} — {@code @NotNull} Integer</li>
 *   <li>{@code projectNo} — {@code @NotBlank} + {@code @Size(max=50)} String</li>
 *   <li>{@code purchaseTaskNo} — {@code @NotBlank} + {@code @Size(max=50)} String</li>
 * </ul>
 *
 * <h3>可选字段（14 个，DB 层可为 NULL 或有默认值）</h3>
 * <ul>
 *   <li>字符串（VARCHAR(50)）：platformCategory / biddingProcess / biddingMethod / purchaseMethod</li>
 *   <li>布尔（TINYINT(1)）：isFixedPrice / creditReportRequired</li>
 *   <li>金额（DECIMAL）：targetCost / documentFee / deposit</li>
 *   <li>时间（DATETIME）：registrationDeadline / techBidDeadline / businessBidDeadline</li>
 *   <li>状态（VARCHAR(20)）：biddingStatus（DB 默认 '已完成'）</li>
 *   <li>备注（TEXT）：notes</li>
 * </ul>
 *
 * <h3>校验触发</h3>
 * Controller 方法参数加 {@code @Valid} 注解（已补全 GlobalExceptionHandler）。
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification
 * @see UpdateRequest
 */
@Data
public class AddRequest {

    // ========== 必填字段（DB NOT NULL） ==========

    @NotNull(message = "经办人ID不能为空")
    private Integer handlerId;

    @NotBlank(message = "项目编号不能为空")
    @Size(max = 50, message = "项目编号长度不能超过50")
    private String projectNo;

    @NotBlank(message = "采购任务编号不能为空")
    @Size(max = 50, message = "采购任务编号长度不能超过50")
    private String purchaseTaskNo;

    // ========== 可选字段（DB 可空或默认值） ==========

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

    @NotBlank(message = "通知邮件收件人不能为空")
    @Size(max = 255, message = "通知邮件收件人长度不能超过255")
    private String targetEmail;
}
