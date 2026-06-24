package io.gitee.lujianshi.bidwinningbackend.dto.supplierdetail;

import io.gitee.lujianshi.bidwinningbackend.controller.SupplierDetailController;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新供应商明细 — 请求 DTO（PATCH 语义）
 *
 * <p>null 字段表示"不修改"。</p>
 *
 * <h3>字段说明</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>{@code supplierName}</td><td>String</td><td>供应商名称</td></tr>
 *   <tr><td>{@code quotePrice}</td><td>BigDecimal</td><td>报价（元）</td></tr>
 *   <tr><td>{@code supplierContact}</td><td>String</td><td>供应商联系方式</td></tr>
 * </table>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail
 * @see AddRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.common.Diff
 * @see SupplierDetailController
 */
@Data
public class UpdateRequest {

    @Size(max = 100, message = "供应商名称长度不能超过100")
    private String supplierName;

    private BigDecimal quotePrice;

    @Size(max = 255, message = "供应商联系方式长度不能超过255")
    private String supplierContact;
}
