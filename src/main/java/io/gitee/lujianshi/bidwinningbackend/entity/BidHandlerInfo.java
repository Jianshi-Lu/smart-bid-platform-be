package io.gitee.lujianshi.bidwinningbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 招标经办人 — 数据库实体。对应表 {@code bid_handler_info}（V18 重命名自 tender_handler_info）。
 *
 * <p>保留 {@code bid} 命名：经办人处理的是 bid 过程（招标 + 投标）的整体协调者。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid_handler_info")
public class BidHandlerInfo {

    @TableId(type = IdType.AUTO)
    private Integer bidHandlerId;

    private String handlerName;

    private String companyName;

    private String departmentName;

    private String targetEmail;
}
