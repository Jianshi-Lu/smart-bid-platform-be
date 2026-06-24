package io.gitee.lujianshi.bidwinningbackend.dto.supplierdetail;

import io.gitee.lujianshi.bidwinningbackend.controller.SupplierDetailController;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建供应商明细 — 请求 DTO
 *
 * <h3>字段说明</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
 *   <tr><td>{@code supplierName}</td><td>String</td><td>是</td><td>供应商名称</td></tr>
 *   <tr><td>{@code quotePrice}</td><td>BigDecimal</td><td>是</td><td>报价（元）</td></tr>
 *   <tr><td>{@code supplierContact}</td><td>String</td><td>是</td><td>供应商联系方式（邮箱/电话/微信）</td></tr>
 * </table>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail
 * @see UpdateRequest
 * @see SupplierDetailController
 */
@Data
public class AddRequest {

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 100, message = "供应商名称长度不能超过100")
    private String supplierName;

    @NotNull(message = "报价不能为空")
    private BigDecimal quotePrice;

    @NotBlank(message = "供应商联系方式不能为空")
    @Size(max = 255, message = "供应商联系方式长度不能超过255")
    private String supplierContact;
}
