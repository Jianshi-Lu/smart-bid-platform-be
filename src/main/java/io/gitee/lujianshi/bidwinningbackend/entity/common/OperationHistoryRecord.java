package io.gitee.lujianshi.bidwinningbackend.entity.common;

import java.time.LocalDateTime;

/**
 * 操作历史记录公共字段接口。
 *
 * <p>五张操作历史表（公告/经办人/投标记录/资信文件/知识文件）结构一致，
 * 由 {@code OperationHistoryService} 统一填充公共字段。</p>
 *
 * @since V33
 */
public interface OperationHistoryRecord {

    void setOperation(String operation);

    void setOperatorType(String operatorType);

    void setOperatorId(Long operatorId);

    void setBeforeSnapshot(String beforeSnapshot);

    void setAfterSnapshot(String afterSnapshot);

    void setRemark(String remark);

    void setCreatedAt(LocalDateTime createdAt);
}
