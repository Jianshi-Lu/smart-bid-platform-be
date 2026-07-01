package io.gitee.lujianshi.bidwinningbackend.dto.invitationforbid;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量新增招标公告（Invitation for Bid）请求 DTO。
 *
 * <p>内层 items 复用 {@link AddRequest}。</p>
 */
@Data
public class BatchAddRequest {

    @NotEmpty(message = "items 不能为空")
    @Size(max = 100, message = "单次批量新增不能超过 100 条")
    private List<@Valid AddRequest> items;
}
