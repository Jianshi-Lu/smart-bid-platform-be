package io.gitee.lujianshi.bidwinningbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class BidHandlerInfoCreateRequest {
    /**
     * 创建经办人请求 DTO
     *
     * 与 Entity 分离的原因：
     * - DTO 是请求载体，Entity 是数据库映射
     * - 字段含义、校验规则可以不同
     */
    @NotBlank(message = "经办人姓名不能为空")
    @Size(max = 50, message = "经办人姓名长度不能超过50")
    private String handlerName;

    @NotBlank(message = "所属公司不能为空")
    @Size(max = 100, message = "公司名称长度不能超过100")
    private String companyName;

    @NotBlank(message = "所属部门不能为空")
    @Size(max = 100, message = "部门名称长度不能超过100")
    private String departmentName;
}
