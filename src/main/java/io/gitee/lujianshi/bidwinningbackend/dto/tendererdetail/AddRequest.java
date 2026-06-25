package io.gitee.lujianshi.bidwinningbackend.dto.tendererdetail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建投标人明细请求 DTO。tendererName / quotePrice / tendererContact 必填，credentials 可选。
 */
@Data
public class AddRequest {

    @NotBlank(message = "投标人名称不能为空")
    @Size(max = 100, message = "投标人名称长度不能超过100")
    private String tendererName;

    @NotNull(message = "报价不能为空")
    private BigDecimal quotePrice;

    @NotBlank(message = "投标人联系方式不能为空")
    @Size(max = 255, message = "投标人联系方式长度不能超过255")
    private String tendererContact;

    @Size(max = 500, message = "资信材料链接长度不能超过500")
    private String credentials;
}
