package io.gitee.lujianshi.bidwinningbackend.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 定标通知 — 数据库实体。对应表 {@code bid_winning_notification}（V4 建表，19 字段）。
 *
 * <p>{@code handlerId} 为逻辑外键，关联 {@code bid_handler_info.handler_id}，DB 层不强制 FK。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid_winning_notification")
public class BidWinningNotification {
    @TableId(type = IdType.AUTO)
    private Integer notificationId;
    @NotNull
    private Integer handlerId;
    @NotBlank
    private String projectNo;
    @NotBlank
    private String purchaseTaskNo;
    private String platformCategory;
    private String biddingProcess;
    private String biddingMethod;
    private String purchaseMethod;
    private Boolean isFixedPrice;
    private BigDecimal targetCost;
    private BigDecimal documentFee;
    private BigDecimal deposit;
    private Boolean creditReportRequired;
    private LocalDateTime registrationDeadline;
    private LocalDateTime techBidDeadline;
    private LocalDateTime businessBidDeadline;
    private String biddingStatus;
    private String notes;
    private LocalDateTime createdAt;

    private String targetEmail;
}
