package io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid;

import io.gitee.lujianshi.bidwinningbackend.entity.common.OperationHistoryRecord;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 招标公告操作历史 — 数据库实体。对应表 {@code invitation_for_bid_operation_history}（V33）。
 *
 * <p>记录公告的创建/修改/删除/状态变更，快照为整行 JSON；删除时保留
 * {@code beforeSnapshot}，创建时保留 {@code afterSnapshot}。</p>
 *
 * @since V33
 */
@Data
@TableName("invitation_for_bid_operation_history")
public class InvitationForBidOperationHistory implements OperationHistoryRecord {

    @TableId(type = IdType.AUTO)
    private Long historyId;

    private Integer invitationId;

    private String operation;

    private String operatorType;

    private Long operatorId;

    private String beforeSnapshot;

    private String afterSnapshot;

    private String remark;

    private LocalDateTime createdAt;
}
