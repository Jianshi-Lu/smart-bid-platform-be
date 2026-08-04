package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.ChatRequest;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChatAgentControllerTest {

    @Test
    void shouldForwardQuestionAndWrapPythonResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        ChatAgentController controller = new ChatAgentController(
                builder.baseUrl("http://127.0.0.1:8082").build());

        server.expect(once(), requestTo("http://127.0.0.1:8082/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("{\"question\":\"什么是招标公告？\"}"))
                .andRespond(withSuccess(
                        "{\"answer\":\"招标公告是采购活动的公告文件。\"}",
                        MediaType.APPLICATION_JSON));

        var result = controller.chat(new ChatRequest("什么是招标公告？"));

        assertEquals("SUCCESS", result.getCode());
        assertEquals("招标公告是采购活动的公告文件。", result.getData().answer());
        server.verify();
    }

    @Test
    void shouldTranslateEmptyPythonAnswerToApplicationException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        ChatAgentController controller = new ChatAgentController(
                builder.baseUrl("http://127.0.0.1:8082").build());

        server.expect(requestTo("http://127.0.0.1:8082/chat"))
                .andRespond(withSuccess(
                        "{\"answer\":\" \"}",
                        MediaType.APPLICATION_JSON));

        AppException exception = assertThrows(
                AppException.class,
                () -> controller.chat(new ChatRequest("问题")));

        assertEquals(
                AppErrorCode.CHAT_AGENT_PYTHON_CALL_FAILED,
                exception.getErrorCode());
        assertEquals("VALIDATE_PYTHON_RESPONSE", exception.getStage());
        server.verify();
    }
}
