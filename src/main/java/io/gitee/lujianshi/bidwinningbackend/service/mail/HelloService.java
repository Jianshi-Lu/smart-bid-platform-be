package io.gitee.lujianshi.bidwinningbackend.service.mail;

import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Service 层：纯业务逻辑
 *
 * 职责：
 * - 编写业务逻辑（与 web 层解耦）
 * - 无任何 {@code @GetMapping} 等 web 路由注解
 * - 不直接操作 HttpServletResponse（Servlet API 属于 web 层）
 *
 * 配置注入：
 * {@code @Value("${key}")} = 从 application.properties 读取配置值
 */
@Service
public class HelloService {

    @Value("${custom.url}")
    private String url;

    /**
     * 业务逻辑：返回 Hello World!
     * Controller 调用此方法获取返回值
     */
    public String hello() {
        return "Hello World!";
    }

    public void accessUrl(HttpServletResponse response) throws IOException {
        response.sendRedirect(url);
    }

    /**
     * 异步任务：延迟3秒后打印"获取地址成功"
     *
     * 执行链路：
     * 1. @Async 注解使方法被 Spring AOP 代理（主线程立即返回，不阻塞）
     * 2. 方法内部调用 AsyncTaskManager.execute(..., 3000)
     * 3. 新线程中 sleep 3秒后，System.out.println 打印日志
     *
     * 注意：@Async + AsyncTaskManager 嵌套使用（双重异步），存在设计冗余，
     * 实际生产中建议只选其一。
     */
    @Async
    public void fetchAfterDelay() {
        AsyncTaskManager.execute(() -> System.out.println("获取地址成功"), 3000);
    }

    /**
     * 返回配置中的 URL
     *
     * @return custom.url 配置值（{@link <a href="https://baidu.com">...</a>}）
     */
    public String fetchUrl() {
        return url;
    }
}