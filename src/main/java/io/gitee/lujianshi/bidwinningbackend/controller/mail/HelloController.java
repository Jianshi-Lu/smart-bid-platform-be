package io.gitee.lujianshi.bidwinningbackend.controller.mail;

import io.gitee.lujianshi.bidwinningbackend.service.mail.HelloService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Controller 层：路由 + 参数校验 + 调用 Service
 *
 * 职责：
 * - 定义 URL 路由（@GetMapping / @PostMapping）
 * - 接收请求参数
 * - 调用 Service 处理业务逻辑
 * - 返回响应
 */
@RestController
public class HelloController {

    @Autowired
    private HelloService helloService;

    @GetMapping("/hello")
    public String hello() {
        return helloService.hello();
    }

    @GetMapping("/access-url")
    public void accessUrl(HttpServletResponse response) throws IOException {
        helloService.accessUrl(response);
    }

    @PostMapping("/fetch-url")
    public String fetchUrl() {
        helloService.fetchAfterDelay();
        return helloService.fetchUrl();
    }
}