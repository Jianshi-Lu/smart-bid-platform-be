package io.gitee.lujianshi.bidwinningbackend.dto.invitationforbid;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 更新招标公告（Invitation for Bid）请求 DTO（PATCH 语义，16 字段全可选，null 字段表示"不修改"）。
 */
@Data
public class UpdateRequest {

    private Integer bidHandlerId;

    @Size(max = 50, message = "项目编号长度不能超过50")
    private String projectNo;

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
