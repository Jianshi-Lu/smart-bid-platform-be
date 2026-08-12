package io.gitee.lujianshi.bidwinningbackend.dto.bidhandler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新招标经办人请求 DTO（PATCH 语义，null 字段表示"不修改"）。
 */
@Data
public class UpdateRequest {

    @Size(max = 50, message = "经办人姓名长度不能超过50")
    private String handlerName;

    @Size(max = 100, message = "公司名称长度不能超过100")
    private String companyName;

    @Size(max = 100, message = "部门名称长度不能超过100")
    private String departmentName;

    private String targetEmail;

    @JsonIgnore
    @AssertTrue(message = "至少提供一个可更新字段")
    public boolean isUpdatePresent() {
        return handlerName != null
                || companyName != null
                || departmentName != null
                || targetEmail != null;
    }
}
