package io.gitee.lujianshi.bidwinningbackend.dto.supplierdetail;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除供应商明细请求 DTO。ids 非空，最多 500 条。
 */
@Data
public class BatchDeleteRequest {

    @NotEmpty(message = "ids 不能为空")
    @Size(max = 500, message = "单次批量删除不能超过 500 条")
    private List<Integer> ids;
}
