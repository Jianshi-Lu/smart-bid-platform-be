package io.gitee.lujianshi.bidwinningbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid_winning_notification")
public class BidWinningNotification {
    @TableId(type = IdType.AUTO)
    private Integer notificationId;
    private Integer handlerId;
    private String projectNo;
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
}
