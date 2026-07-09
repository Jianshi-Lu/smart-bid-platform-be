package io.gitee.lujianshi.bidwinningbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.gitee.lujianshi.bidwinningbackend.enums.BidNoticeStatus;

/**
 * 招标公告状态变更历史 — 数据库实体。对应表 {@code invitation_for_bid_status_history}。
 *
 * <p>用于审计 {@link InvitationForBid} 的状态机变更；{@code invitationId} 是逻辑外键，
 * DB 层不强制 FK 约束。</p>
 *
 * @see BidNoticeStatus
 * @since V18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("invitation_for_bid_status_history")
public class InvitationForBidStatusHistory {
    @TableId(type = IdType.AUTO)
    private Integer historyId;
    private Integer invitationId;
    private BidNoticeStatus fromStatus;
    private BidNoticeStatus toStatus;
    private String remark;
    private LocalDateTime createdAt;
}
