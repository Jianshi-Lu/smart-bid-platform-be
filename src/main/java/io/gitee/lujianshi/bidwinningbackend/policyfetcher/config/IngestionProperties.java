package io.gitee.lujianshi.bidwinningbackend.policyfetcher.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 政策资讯采集Python服务配置。 */
@ConfigurationProperties(prefix = "policy-fetcher")
@Getter
@Setter
public class IngestionProperties {

    /** Python政策资讯采集服务基础地址。 */
    private String pythonBaseUrl = "http://127.0.0.1";
}
