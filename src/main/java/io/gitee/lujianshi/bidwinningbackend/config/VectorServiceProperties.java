package io.gitee.lujianshi.bidwinningbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 独立 vector-service 的 HTTP 连接配置。 */
@ConfigurationProperties(prefix = "vector-service")
@Getter
@Setter
public class VectorServiceProperties {

    /** 文档切分、Embedding、Chroma 入库与检索服务基础地址。 */
    private String baseUrl = "http://127.0.0.1:8083";
}
