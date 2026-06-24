package io.gitee.lujianshi.bidwinningbackend.dto.supplierdetail;

import io.gitee.lujianshi.bidwinningbackend.controller.SupplierDetailController;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除供应商明细 — 请求 DTO
 *
 * <h3>字段说明</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
 *   <tr><td>{@code ids}</td><td>List&lt;Integer&gt;</td><td>是</td><td>待删除的供应商 ID 列表</td></tr>
 * </table>
 *
 * @see SupplierDetailController
 */
@Data
public class BatchDeleteRequest {

    @NotEmpty(message = "ids 不能为空")
    @Size(max = 500, message = "单次批量删除不能超过 500 条")
    private List<Integer> ids;
}
