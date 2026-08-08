package io.gitee.lujianshi.bidwinningbackend.chatagent.dto;

import jakarta.validation.constraints.Size;

/** 创建会话请求；阶段 0 允许前端不传标题。 */
public record CreateConversationRequest(
        @Size(max = 200, message = "会话标题长度不能超过 200")
        String title) {
}
