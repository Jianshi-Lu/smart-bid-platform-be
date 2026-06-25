package io.gitee.lujianshi.bidwinningbackend.dto.tendernotice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 招标公告状态变更请求 DTO。
 */
@Data
public class StatusUpdateRequest {

    @NotBlank(message = "状态不能为空")
    @Size(max = 20, message = "状态长度不能超过20")
    private String status;

    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
}
