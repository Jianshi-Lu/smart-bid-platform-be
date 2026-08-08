package io.gitee.lujianshi.bidwinningbackend.entity.bidrecord;

import io.gitee.lujianshi.bidwinningbackend.entity.common.OperationHistoryRecord;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投标记录操作历史 — 数据库实体。对应表 {@code bid_record_operation_history}（V35）。
 *
 * <p>记录投标记录的创建/修改/删除，快照为整行 JSON；招标方代录与投标方
 * 自己的操作（/bidder/bid-records/*）都会写入本表。</p>
 *
 * @since V35
 */
@Data
@TableName("bid_record_operation_history")
public class BidRecordOperationHistory implements OperationHistoryRecord {

    @TableId(type = IdType.AUTO)
    private Long historyId;

    private Integer bidRecordId;

    private String operation;

    private String operatorType;

    private Long operatorId;

    private String beforeSnapshot;

    private String afterSnapshot;

    private String remark;

    private LocalDateTime createdAt;
}
