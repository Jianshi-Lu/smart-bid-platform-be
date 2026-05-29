package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Service 层：纯业务逻辑
 *
 * @Service = 标注为 Spring 容器中的 Bean，被 @Autowired 注入到 Controller
 *
 * 职责：
 * - 编写业务逻辑（与 web 层解耦）
 * - 无任何 @GetMapping 等web路由注解（不是不允许注解）
 * - 不直接操作 HttpServletResponse（Servlet API 属于 web 层）
 *
 * 配置注入：
 * @Value("${key}") = 从 application.properties 读取配置值
 * 工作流程：Spring 启动时加载配置文件到 Environment → AutowiredAnnotationBeanPostProcessor 扫描 @Value → 从 Environment 取值注入
 */
@Service
public class HelloService {

    /**
     * @Value 注入配置值
     * Spring 启动时从 application.properties 读取 server.port=8079
     * 注意：字段不能是 static，必须是实例字段
     */
    @Value("${server.port}")
    private int port;

    /**
     * @Value 从 application.properties 读取 custom.url=https://baidu.com
     */
    @Value("${custom.url}")
    private String url;

    /**
     * 业务逻辑：返回 Hello World!
     * Controller 调用此方法获取返回值
     */
    public String hello() {
        return "Hello World!";
    }

    /**
     * 业务逻辑：提供重定向目标 URL
     * 注意：此方法接收 HttpServletResponse 是为了演示，实际推荐返回 URL 字符串给 Controller 处理
     * 这样 Service 层更纯粹，易于单元测试，不耦合 Servlet API
     */
    public void accessUrl(HttpServletResponse response) throws IOException {
        response.sendRedirect(url);
    }

    @Async
    public void fetchAfterDelay() {
        AsyncTaskManager.execute(()-> {
            System.out.println("获取地址成功");
            }, 3000
        );
    }


    public String fetchUrl() {
        return url;
    }
}