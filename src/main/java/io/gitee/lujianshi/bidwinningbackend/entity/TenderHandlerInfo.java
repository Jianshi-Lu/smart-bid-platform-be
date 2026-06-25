package io.gitee.lujianshi.bidwinningbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 招标经办人 — 数据库实体。对应表 {@code tender_handler_info}（V13 重命名）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tender_handler_info")
public class TenderHandlerInfo {

    @TableId(type = IdType.AUTO)
    private Integer tenderHandlerId;

    private String handlerName;

    private String companyName;

    private String departmentName;

    private String targetEmail;
}
