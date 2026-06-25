package io.gitee.lujianshi.bidwinningbackend.dto.supplierdetail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建供应商明细请求 DTO。supplierName / quotePrice / supplierContact 均必填。
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
