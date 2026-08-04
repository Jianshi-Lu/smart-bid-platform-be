package io.gitee.lujianshi.bidwinningbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** chat-agent 独立 MySQL 数据源配置。 */
@ConfigurationProperties(prefix = "chat-agent.datasource")
@Getter
@Setter
public class ChatAgentDataSourceProperties {

    /** chat_agent 数据库 JDBC 地址。 */
    private String url;

    /** chat_agent 数据库用户名。 */
    private String username;

    /** chat_agent 数据库密码。 */
    private String password;

    /** MySQL JDBC 驱动类名。 */
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
}
