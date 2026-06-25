package io.gitee.lujianshi.bidwinningbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 招标公告状态变更历史 — 数据库实体。对应表 {@code tender_notice_status_history}（V15 建表）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tender_notice_status_history")
public class TenderNoticeStatusHistory {
    @TableId(type = IdType.AUTO)
    private Integer historyId;
    private Integer noticeId;
    private String fromStatus;
    private String toStatus;
    private String remark;
    private LocalDateTime createdAt;
}
