package io.gitee.lujianshi.bidwinningbackend.dto.tenderhandler;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建经办人请求 DTO。3 个业务字段 + 通知邮件收件人均必填。
 */
@Data
public class AddRequest {

    @NotBlank(message = "经办人姓名不能为空")
    @Size(max = 50, message = "经办人姓名长度不能超过50")
    private String handlerName;

    @NotBlank(message = "所属公司不能为空")
    @Size(max = 100, message = "公司名称长度不能超过100")
    private String companyName;

    @NotBlank(message = "所属部门不能为空")
    @Size(max = 100, message = "部门名称长度不能超过100")
    private String departmentName;

    @NotBlank(message = "通知邮件收件人不能为空")
    @Size(max = 255, message = "通知邮件收件人长度不能超过255")
    private String targetEmail;
}
