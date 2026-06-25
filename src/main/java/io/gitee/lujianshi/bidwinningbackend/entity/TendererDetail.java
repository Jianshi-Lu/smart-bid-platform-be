package io.gitee.lujianshi.bidwinningbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 投标人明细 — 数据库实体。对应表 {@code tenderer_detail}（V16 重命名自 supplier_detail，5 字段）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tenderer_detail")
public class TendererDetail {

    @TableId(type = IdType.AUTO)
    private Integer tendererId;

    private String tendererName;

    private BigDecimal quotePrice;

    private String tendererContact;

    private String credentials;
}
