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
 * 招标公告状态变更历史 — 数据库实体。对应表 {@code invitation_for_bid_status_history}（V18 重命名自 tender_notice_status_history）。
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
