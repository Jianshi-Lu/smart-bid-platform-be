package io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.gitee.lujianshi.bidwinningbackend.enums.BidNoticeStatus;

/**
 * 招标公告 — 数据库实体。对应表 {@code invitation_for_bid}（V18 重命名自 tender_notice）。
 *
 * <p>命名遵循 World Bank 术语规范："Invitation for Bids"。</p>
 *
 * <p>{@code bidHandlerId} 为逻辑外键，关联 {@code bid_handler_info.bid_handler_id}，DB 层不强制 FK。</p>
 *
 * <p>{@code invitationId} 为 PK；命名采用短 ID 形式（项目约定，去掉表名后缀）。</p>
 *
 * @see BidNoticeStatus
 * @since V18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("invitation_for_bid")
public class InvitationForBid {
    @TableId(type = IdType.AUTO)
    private Integer invitationId;
    private Integer bidHandlerId;
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
    private BidNoticeStatus status;
}
