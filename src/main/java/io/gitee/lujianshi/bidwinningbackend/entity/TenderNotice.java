package io.gitee.lujianshi.bidwinningbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.gitee.lujianshi.bidwinningbackend.enums.TenderNoticeStatus;

/**
 * 招标公告 — 数据库实体。对应表 {@code tender_notice}（V4 建表，V14 重构，V15 加 status，17 字段）。
 *
 * <p>{@code tenderHandlerId} 为逻辑外键，关联 {@code tender_handler_info.tender_handler_id}，DB 层不强制 FK。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tender_notice")
public class TenderNotice {
    @TableId(type = IdType.AUTO)
    private Integer noticeId;
    private Integer tenderHandlerId;
    private String projectNo;
    private String purchaseTaskNo;
    private String platformCategory;
    private String tenderProcess;
    private String tenderMethod;
    private String purchaseMethod;
    private Boolean isFixedPrice;
    private Boolean hasDocumentFee;
    private BigDecimal deposit;
    private Boolean creditReportRequired;
    private LocalDateTime registrationDeadline;
    private LocalDateTime technicalTenderDeadline;
    private LocalDateTime businessTenderDeadline;
    private String notes;
    private LocalDateTime createdAt;
    private String purchaseCompany;
    private String purchaseDepartment;
    private String tenderingParty;
    private TenderNoticeStatus status;
}
