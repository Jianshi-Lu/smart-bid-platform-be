package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.chatagent.ChatAgentConversationService;
import io.gitee.lujianshi.bidwinningbackend.dto.ChatRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.ChatResponse;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAgentControllerTest {

    @Test
    void shouldDelegateConversationAwareChat() {
        ChatAgentConversationService service =
                mock(ChatAgentConversationService.class);
        ChatAgentController controller = new ChatAgentController(service);
        ChatRequest request = new ChatRequest(
                "conversation-1", "client-message-1", "什么是招标公告？");
        when(service.chat(any(ChatRequest.class))).thenReturn(new ChatResponse(
                "conversation-1", "request-1", "client-message-1",
                "招标公告是采购活动的公告文件。"));

        Result<ChatResponse> result = controller.chat(request);

        assertEquals("SUCCESS", result.getCode());
        assertEquals("conversation-1", result.getData().conversationId());
        assertEquals("request-1", result.getData().requestId());
        assertEquals("招标公告是采购活动的公告文件。",
                result.getData().answer());
    }
}
