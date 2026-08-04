package io.gitee.lujianshi.bidwinningbackend.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.Objects;

/** 配置 chat-agent 使用的独立数据源、MyBatis 会话工厂和事务管理器。 */
@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = "io.gitee.lujianshi.bidwinningbackend.chatagent.mapper",
        sqlSessionFactoryRef = "chatAgentSqlSessionFactory")
public class ChatAgentDataSourceConfig {

    /** 创建不参与主库自动配置的 chat_agent 数据源。 */
    @Bean(name = "chatAgentDataSource")
    public DataSource chatAgentDataSource(ChatAgentDataSourceProperties properties) {
        return DataSourceBuilder.create()
                .driverClassName(properties.getDriverClassName())
                .url(properties.getUrl())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .build();
    }

    /** 创建只加载 chatagent Mapper XML 的独立 MyBatis 会话工厂。 */
    @Bean(name = "chatAgentSqlSessionFactory")
    public SqlSessionFactory chatAgentSqlSessionFactory(
            @Qualifier("chatAgentDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeAliasesPackage(
                "io.gitee.lujianshi.bidwinningbackend.chatagent.entity");
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/chatagent/*.xml"));
        factory.setFailFast(true);
        return Objects.requireNonNull(
                factory.getObject(), "chatAgentSqlSessionFactory 创建失败");
    }

    /** 创建 chat_agent 专用事务管理器，避免事务误用主库。 */
    @Bean(name = "chatAgentTransactionManager")
    public DataSourceTransactionManager chatAgentTransactionManager(
            @Qualifier("chatAgentDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
