package io.gitee.lujianshi.bidwinningbackend.chatagent.dto;

import java.util.List;

/** Java 平台返回的已持久化问答结果。 */
public record ChatResponse(
        String conversationId,
        String requestId,
        String clientMessageId,
        String answer,
        String title,
        List<Integer> referencedBidRecordIds) {
}
