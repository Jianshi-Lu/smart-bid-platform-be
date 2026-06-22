package io.gitee.lujianshi.bidwinningbackend.dto.bidhandlerinfo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量新增经办人 — 请求 DTO。
 *
 * <p>外层 {@code targetEmail} 指定汇总邮件收件人（非空时所有记录汇总到 1 封邮件）；
 * 为空/blank 时不发送汇总邮件。内层 {@code items} 复用 {@link AddRequest}，
 * 每条记录的 targetEmail 各自落库，不参与邮件路由。</p>
 *
 * <h3>请求示例</h3>
 * <pre>
 * POST /bid-handler-info/batch-add
 * Content-Type: application/json
 *
 * {
 *   "targetEmail": "manager@company.com",
 *   "items": [
 *     {"handlerName":"张三","companyName":"北京科技","departmentName":"采购部","targetEmail":"zhang@x.com"},
 *     {"handlerName":"李四","companyName":"上海信息","departmentName":"招标部","targetEmail":"li@x.com"}
 *   ]
 * }
 * </pre>
 */
@Data
public class BatchAddRequest {

    /**
     * 汇总邮件收件人（可选）。
     * <p>非空时，所有新增记录汇总到 1 封邮件发送至此地址；
     * 为空/blank 时不发送汇总邮件。</p>
     */
    private String targetEmail;

    @NotEmpty(message = "items 不能为空")
    @Size(max = 100, message = "单次批量新增不能超过 100 条")
    private List<@Valid AddRequest> items;
}
