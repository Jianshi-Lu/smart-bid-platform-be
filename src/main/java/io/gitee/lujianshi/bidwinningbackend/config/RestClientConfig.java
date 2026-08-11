package io.gitee.lujianshi.bidwinningbackend.config;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileConverterProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 为各个 Python 下游服务集中构造可复用的 {@link RestClient}。
 *
 * <p>不同下游服务保留独立 Bean，分别绑定自己的基础地址；Controller 或客户端只
 * 注入已经构造完成的实例，不在每次请求中重新创建 RestClient。</p>
 */
@Configuration
public class RestClientConfig {

    /** 构造 chat-agent Python 服务客户端。 */
    @Bean("chatAgentRestClient")
    public RestClient chatAgentRestClient(
            RestClient.Builder builder,
            ChatAgentProperties properties) {
        return builder.clone()
                .baseUrl(properties.getPythonBaseUrl())
                .build();
    }

    /** 构造独立 vector-service 客户端。 */
    @Bean("vectorServiceRestClient")
    public RestClient vectorServiceRestClient(
            RestClient.Builder builder,
            VectorServiceProperties properties) {
        return builder.clone()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /** 构造 to-markdown Python 服务客户端。 */
    @Bean("toMarkdownRestClient")
    public RestClient toMarkdownRestClient(
            RestClient.Builder builder,
            FileConverterProperties properties) {
        return builder.clone()
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
