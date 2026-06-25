package io.gitee.lujianshi.bidwinningbackend.dto.supplierdetail;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新供应商明细请求 DTO（PATCH 语义，null 字段表示"不修改"）。
 */
@Data
public class UpdateRequest {

    @Size(max = 100, message = "供应商名称长度不能超过100")
    private String supplierName;

    private BigDecimal quotePrice;

    @Size(max = 255, message = "供应商联系方式长度不能超过255")
    private String supplierContact;
}
