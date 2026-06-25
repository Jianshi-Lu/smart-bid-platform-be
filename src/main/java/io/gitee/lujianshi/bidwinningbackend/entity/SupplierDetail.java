package io.gitee.lujianshi.bidwinningbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 供应商明细 — 数据库实体。对应表 {@code supplier_detail}（V10 重构，4 字段）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("supplier_detail")
public class SupplierDetail {

    @TableId(type = IdType.AUTO)
    private Integer supplierId;

    private String supplierName;

    private BigDecimal quotePrice;

    private String supplierContact;
}
