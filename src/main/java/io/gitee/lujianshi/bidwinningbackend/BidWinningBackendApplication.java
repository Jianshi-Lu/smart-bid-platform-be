package io.gitee.lujianshi.bidwinningbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot 启动入口
 *
 * 组件扫描范围：io.gitee.lujianshi.bidwinningbackend 包及其子包下的所有组件注解类
 * - @Component、@Service、@Controller、@Repository、@Configuration 都会被扫描注册为 Bean
 * - @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
 *
 * 工作流程：
 * SpringApplication.run() → 创建嵌入式 Tomcat → 初始化 DispatcherServlet →
 * 扫描 @RestController → 注册 @GetMapping 到 URL 映射表 → 请求进来 → DispatcherServlet 查表路由
 */
@SpringBootApplication
@EnableAsync  // 开启异步支持
public class BidWinningBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BidWinningBackendApplication.class, args);
    }
}