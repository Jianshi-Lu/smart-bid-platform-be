package io.gitee.lujianshi.bidwinningbackend.knowledgeingestionagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** knowledge-ingestion-agent Python服务配置。 */
@ConfigurationProperties(prefix = "knowledge-ingestion-agent")
@Getter
@Setter
public class KnowledgeIngestionAgentProperties {

    /** Python政策资讯采集服务基础地址。 */
    private String pythonBaseUrl = "http://127.0.0.1";
}
