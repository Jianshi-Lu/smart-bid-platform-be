package io.gitee.lujianshi.bidwinningbackend.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * 主业务库数据源与 MyBatis 配置。
 *
 * <p>因为项目自定义了 chat-agent 数据源/会话工厂，Spring Boot 的
 * {@code DataSourceAutoConfiguration} 与 MyBatis/MyBatis-Plus 自动配置会因
 * {@code @ConditionalOnMissingBean} 退出，因此主库的数据源和会话工厂必须显式声明。</p>
 */
@Configuration(proxyBeanMethods = false)
public class MainDataSourceConfig {

    /** 主业务库 DataSource，name 固定为 dataSource 以兼容既有依赖注入。 */
    @Bean(name = "dataSource")
    @Primary
    public DataSource dataSource(Environment env) {
        DataSourceBuilder<?> builder = DataSourceBuilder.create()
                .url(env.getProperty("spring.datasource.url"))
                .username(env.getProperty("spring.datasource.username"))
                .password(env.getProperty("spring.datasource.password"));
        String driverClassName = env.getProperty("spring.datasource.driver-class-name");
        if (driverClassName != null && !driverClassName.isBlank()) {
            builder.driverClassName(driverClassName);
        }
        return builder.build();
    }

    /** 主业务库的 MyBatis 会话工厂，只加载主库 Mapper XML。 */
    @Bean(name = "mainSqlSessionFactory")
    @Primary
    public SqlSessionFactory mainSqlSessionFactory(
            @Qualifier("dataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeAliasesPackage(
                "io.gitee.lujianshi.bidwinningbackend.entity");
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/*.xml"));
        factory.setFailFast(true);
        return Objects.requireNonNull(
                factory.getObject(), "mainSqlSessionFactory 创建失败");
    }
}
