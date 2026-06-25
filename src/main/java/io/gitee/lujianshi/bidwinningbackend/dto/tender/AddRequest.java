package io.gitee.lujianshi.bidwinningbackend.dto.tender;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建招标公告请求 DTO。3 字段必填（tenderHandlerId / projectNo / purchaseTaskNo），其余 14 字段可选。
 */
@Data
public class AddRequest {

    @NotNull(message = "经办人ID不能为空")
    private Integer tenderHandlerId;

    @NotBlank(message = "项目编号不能为空")
    @Size(max = 50, message = "项目编号长度不能超过50")
    private String projectNo;

    @NotBlank(message = "采购任务编号不能为空")
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

    @NotBlank(message = "通知邮件收件人不能为空")
    @Size(max = 255, message = "通知邮件收件人长度不能超过255")
    private String targetEmail;
}
