package io.gitee.lujianshi.bidwinningbackend.entity.bidhandler;

import io.gitee.lujianshi.bidwinningbackend.entity.common.OperationHistoryRecord;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 招标经办人操作历史 — 数据库实体。对应表 {@code bid_handler_info_operation_history}（V34）。
 *
 * <p>记录经办人的创建/修改/删除，快照为整行 JSON。</p>
 *
 * @since V34
 */
@Data
@TableName("bid_handler_info_operation_history")
public class BidHandlerInfoOperationHistory implements OperationHistoryRecord {

    @TableId(type = IdType.AUTO)
    private Long historyId;

    private Integer bidHandlerId;

    private String operation;

    private String operatorType;

    private Long operatorId;

    private String beforeSnapshot;

    private String afterSnapshot;

    private String remark;

    private LocalDateTime createdAt;
}
