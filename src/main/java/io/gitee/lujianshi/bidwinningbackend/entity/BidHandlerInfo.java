package io.gitee.lujianshi.bidwinningbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid_handler_info")
public class BidHandlerInfo {
    @TableId(type = IdType.AUTO)

    private Integer handlerId;
    private String handlerName;
    private String companyName;
    private String departmentName;
}
