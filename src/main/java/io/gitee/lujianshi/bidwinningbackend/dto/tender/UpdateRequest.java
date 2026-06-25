package io.gitee.lujianshi.bidwinningbackend.dto.tender;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 更新定标通知请求 DTO（PATCH 语义，17 字段全可选，null 字段表示"不修改"）。
 */
@Data
public class UpdateRequest {

    private Integer tenderHandlerId;

    @Size(max = 50, message = "项目编号长度不能超过50")
    private String projectNo;

    @Size(max = 50, message = "采购任务编号长度不能超过50")
    private String purchaseTaskNo;

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

    private String targetEmail;
}
