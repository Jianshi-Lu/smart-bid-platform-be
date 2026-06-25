package io.gitee.lujianshi.bidwinningbackend.dto.tendererdetail;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新投标人明细请求 DTO（PATCH 语义，null 字段表示"不修改"）。
 */
@Data
public class UpdateRequest {

    @Size(max = 100, message = "投标人名称长度不能超过100")
    private String tendererName;

    private BigDecimal quotePrice;

    @Size(max = 255, message = "投标人联系方式长度不能超过255")
    private String tendererContact;

    @Size(max = 500, message = "资信材料链接长度不能超过500")
    private String credentials;
}
