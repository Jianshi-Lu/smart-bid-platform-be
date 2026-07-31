package io.gitee.lujianshi.bidwinningbackend.credentialfile.client;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileConverterProperties;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ToMarkdownClientTest {

    @Test
    void shouldSendMultipartFileAndAcceptMarkdownResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        ToMarkdownClient client = createClient(builder);
        byte[] source = "source-content".getBytes(StandardCharsets.UTF_8);
        byte[] markdown = "# 转换结果".getBytes(StandardCharsets.UTF_8);

        server.expect(once(), requestTo("http://127.0.0.1:8081/convert"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MediaType requestType = request.getHeaders().getContentType();
                    assertEquals("multipart", requestType.getType());
                    assertEquals("form-data", requestType.getSubtype());
                    String requestBody = ((MockClientHttpRequest) request)
                            .getBodyAsString();
                    assertTrue(requestBody.contains("name=\"file\""));
                    assertTrue(requestBody.contains("license.pdf"));
                    assertTrue(requestBody.contains("source-content"));
                })
                .andRespond(withSuccess(
                                markdown,
                                MediaType.parseMediaType(
                                        "text/markdown; charset=utf-8"))
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"converted.md\"; "
                                        + "filename*=UTF-8''license.md"));

        PythonMarkdownResponse response = client.convert(
                new ByteArrayInputStream(source),
                "license.pdf",
                "application/pdf",
                source.length);

        assertArrayEquals(markdown, response.markdown());
        assertEquals("license.md", response.filename());
        assertEquals(
                "text/markdown;charset=utf-8",
                response.contentType());
        server.verify();
    }

    @Test
    void shouldRejectSuccessfulResponseThatIsNotMarkdown() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        ToMarkdownClient client = createClient(builder);
        byte[] source = "source-content".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo("http://127.0.0.1:8081/convert"))
                .andRespond(withSuccess(
                        "{\"markdown\":\"wrong envelope\"}",
                        MediaType.APPLICATION_JSON));

        AppException exception = assertThrows(
                AppException.class,
                () -> client.convert(
                        new ByteArrayInputStream(source),
                        "license.pdf",
                        "application/pdf",
                        source.length));

        assertEquals(
                "VALIDATE_PYTHON_RESPONSE",
                exception.getStage());
        server.verify();
    }

    private ToMarkdownClient createClient(RestClient.Builder builder) {
        FileConverterProperties properties = new FileConverterProperties();
        properties.setBaseUrl("http://127.0.0.1:8081");
        return new ToMarkdownClient(builder, properties);
    }
}
