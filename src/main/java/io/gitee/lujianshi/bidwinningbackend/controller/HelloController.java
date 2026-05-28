package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.service.HelloService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Controller 层：路由 + 参数校验 + 调用 Service
 *
 * @RestController = @Controller + @ResponseBody（所有方法返回对象序列化为 JSON/字符串，不走视图模板）
 *
 * 职责：
 * - 定义 URL 路由（@GetMapping）
 * - 接收请求参数（可选）
 * - 调用 Service 处理业务逻辑
 * - 返回响应（Controller 决定如何包装返回值）
 *
 * 不该做的事：业务逻辑直接写在方法里
 */
@RestController
public class HelloController {

    /**
     * @Autowired = 依赖注入（DI）
     * Spring 自动找到 HelloService（@Service 标注）并注入到这里
     * 减少了在方法内部 new 对象的环节
     */
    @Autowired
    private HelloService helloService;

    /**
     * @GetMapping("/hello") = 绑定 URL 路径到方法
     * 完整 URL = server.port（8080） + @GetMapping 路径
     * Spring Boot 启动时扫描所有 @RestController，将 @GetMapping 注册到 URL 映射表
     * 请求进来 → DispatcherServlet 查表 → 找到 hello() → 自动调用
     */
    @GetMapping("/hello")
    public String hello() {
        return helloService.hello();
    }

    /**
     * @GetMapping("/custom") 路由到百度重定向
     * 业务逻辑委托给 Service，返回目标 URL
     * Controller 负责 HTTP 层操作（sendRedirect）
     */
    @GetMapping("/access-url")
    public void accessUrl(HttpServletResponse response) throws IOException {
        helloService.accessUrl(response);
    }

    @PostMapping("/fetch-url")
    public void fetchUrl() {
        helloService.fetchUrl();
        helloService.fetchAfterDelay();
    }
}