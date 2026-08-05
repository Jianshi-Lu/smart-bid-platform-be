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

    /**
     * 阶段 0 的单用户边界。当前尚未接入登录态，因此不从前端接收 userId。
     * 后续接入鉴权后，由当前用户上下文替换该配置值。
     */
    private Long userId = 1L;
}
