package io.gitee.lujianshi.bidwinningbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 供应商明细 — 实体
 *
 * <p>对应表 {@code supplier_detail}（V10 重构）。
 * 记录中标供应商的名称、报价和联系方式，与 {@code bid_winning_notification} 通过项目编号间接关联。
 * 与 {@code risk_analysis} 通过 {@code supplierId} 关联。</p>
 *
 * <h3>字段说明</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>supplierId</td><td>Integer</td><td>供应商 ID（主键，自增）</td></tr>
 *   <tr><td>supplierName</td><td>String</td><td>供应商名称</td></tr>
 *   <tr><td>quotePrice</td><td>BigDecimal</td><td>报价（元）</td></tr>
 *   <tr><td>supplierContact</td><td>String</td><td>供应商联系方式（邮箱/电话/微信）</td></tr>
 * </table>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.mapper.SupplierDetailMapper
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
