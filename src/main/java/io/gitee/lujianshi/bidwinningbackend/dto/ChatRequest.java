package io.gitee.lujianshi.bidwinningbackend.dto;

import jakarta.validation.constraints.NotBlank;

/** 提交给 chat-agent 的单轮无状态问答请求。 */
public record ChatRequest(
        @NotBlank(message = "问题不能为空")
        String question) {
}
