package io.gitee.lujianshi.bidwinningbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot 启动入口。
 *
 * <p>{@code @EnableAsync} 开启异步支持；{@code @MapperScan} 扫描 mapper 包。</p>
 */
@SpringBootApplication
@EnableAsync
@EnableAspectJAutoProxy
@MapperScan("io.gitee.lujianshi.bidwinningbackend.mapper")
public class BidWinningBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BidWinningBackendApplication.class, args);
    }
}
