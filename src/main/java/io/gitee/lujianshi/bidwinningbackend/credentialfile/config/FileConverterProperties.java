package io.gitee.lujianshi.bidwinningbackend.credentialfile.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** to-markdown Python 服务配置。 */
@ConfigurationProperties(prefix = "credential-file.converter")
@Getter
@Setter
public class FileConverterProperties {

    /** Python 服务基础地址，例如 http://127.0.0.1:8081。 */
    private String baseUrl = "http://127.0.0.1:8081";
}
