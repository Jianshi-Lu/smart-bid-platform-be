package io.gitee.lujianshi.bidwinningbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 启动入口。
 *
 * <p>{@code @EnableAsync} 开启异步支持；{@code @MapperScan} 扫描全局 Mapper 与资信文件业务模块 Mapper。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy
@MapperScan(
        basePackages = {
                "io.gitee.lujianshi.bidwinningbackend.mapper",
                "io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper"
        },
        sqlSessionFactoryRef = "mainSqlSessionFactory")
public class BidWinningBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BidWinningBackendApplication.class, args);
    }
}
