package io.gitee.lujianshi.bidwinningbackend.dto.bidhandler;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量新增招标经办人请求 DTO。
 *
 * <p>外层 {@code targetEmail} 指定汇总邮件收件人（非空时所有记录汇总到 1 封邮件）；
 * 为空/blank 时不发送汇总邮件。内层 items 复用 {@link AddRequest}，
 * 每条记录的 targetEmail 各自落库，不参与邮件路由。</p>
 */
@Data
public class BatchAddRequest {

    /** 汇总邮件收件人（可选，非空时所有记录汇总到 1 封邮件；为空则不发）。 */
    private String targetEmail;

    @NotEmpty(message = "items 不能为空")
    @Size(max = 100, message = "单次批量新增不能超过 100 条")
    private List<@Valid AddRequest> items;
}
