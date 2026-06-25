package io.gitee.lujianshi.bidwinningbackend.dto.tendernotice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建招标公告请求 DTO。3 字段必填（tenderHandlerId / projectNo / purchaseTaskNo），其余 13 字段可选。
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
    private String tenderProcess;

    @Size(max = 50, message = "招标方式长度不能超过50")
    private String tenderMethod;

    @Size(max = 50, message = "采购方式长度不能超过50")
    private String purchaseMethod;

    private Boolean isFixedPrice;

    private Boolean hasDocumentFee;

    private BigDecimal deposit;

    private Boolean creditReportRequired;

    private LocalDateTime registrationDeadline;

    private LocalDateTime technicalTenderDeadline;

    private LocalDateTime businessTenderDeadline;

    private String notes;

    @Size(max = 100, message = "采购公司长度不能超过100")
    private String purchaseCompany;

    @Size(max = 100, message = "采购部门长度不能超过100")
    private String purchaseDepartment;

    @Size(max = 100, message = "招标主体长度不能超过100")
    private String tenderingParty;
}
