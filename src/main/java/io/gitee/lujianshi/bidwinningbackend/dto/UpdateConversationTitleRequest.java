package io.gitee.lujianshi.bidwinningbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 手动修改会话标题的请求。 */
public record UpdateConversationTitleRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max = 200, message = "会话标题长度不能超过 200")
        String title) {
}
