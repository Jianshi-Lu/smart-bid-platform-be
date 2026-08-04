package io.gitee.lujianshi.bidwinningbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** chat-agent Python 服务配置。 */
@ConfigurationProperties(prefix = "chat-agent")
@Getter
@Setter
public class ChatAgentProperties {

    /** Python chat-agent 服务基础地址。 */
    private String pythonBaseUrl = "http://127.0.0.1:8082";
}
