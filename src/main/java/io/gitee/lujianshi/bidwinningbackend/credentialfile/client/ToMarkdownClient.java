package io.gitee.lujianshi.bidwinningbackend.credentialfile.client;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonFileConvertRequest;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonFileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileConverterProperties;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Java 调用 to-markdown 的唯一 HTTP 客户端。 */
@Component
public class ToMarkdownClient {

    private final RestClient restClient;

    public ToMarkdownClient(
            RestClient.Builder restClientBuilder,
            FileConverterProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /** 请求 Python 转换一个 MinIO original 对象。 */
    public PythonFileConvertResponse convertObject(String originalObjectKey) {
        try {
            PythonFileConvertResponse response = restClient.post()
                    .uri("/convert/object")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PythonFileConvertRequest(originalObjectKey))
                    .retrieve()
                    .body(PythonFileConvertResponse.class);
            if (response == null) {
                throw new IllegalStateException("Python 转换服务返回为空");
            }
            return response;
        } catch (RestClientException e) {
            throw new IllegalStateException("调用 Python 转换服务失败", e);
        }
    }
}
