package io.gitee.lujianshi.bidwinningbackend.dto.bidhandler;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除招标经办人请求 DTO。ids 非空，最多 500 条。
 */
@Data
public class DeleteRequest {

    @NotEmpty(message = "ids 不能为空")
    @Size(max = 500, message = "单次批量删除不能超过 500 条")
    private List<Integer> ids;
}
