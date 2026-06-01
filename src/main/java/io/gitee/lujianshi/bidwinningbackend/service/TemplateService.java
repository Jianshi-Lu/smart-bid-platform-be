package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.NotificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Thymeleaf 模板解析服务
 *
 * @Service = 标注为 Spring 容器中的 Bean，被 @Autowired 注入到其他 Bean
 *
 * 功能：
 * - 读取 src/main/resources/templates/ 下的 Thymeleaf 模板
 * - 用 NotificationRequest 的数据填充模板，生成最终 HTML 字符串
 *
 * Spring Boot 配置（ThymeleafAutoConfiguration）：
 * - 默认路径：classpath:/templates/
 * - 实际读取 classpath:/templates/bid-winning-notification.html（properties配置）
 */
@Service
public class TemplateService {

    @Autowired
    private SpringTemplateEngine templateEngine;

    /**
     * 处理模板：用 NotificationRequest 数据填充 Thymeleaf 模板
     *
     * Context 包说明（org.thymeleaf.context）：
     * - Context 是 Thymeleaf 的"模板数据容器"，封装了一个 Map + 一个 Locale
     * - 模板引擎渲染时，遇到 ${变量名} → 调用 context.getVariable("变量名")
     * - 底层使用 OGNL/SpringEL 表达式解析，从 Map 里取值并填充到 HTML
     *
     * 使用步骤：
     * 1. new Context() 创建上下文
     * 2. context.setVariable("key", value) 放入数据（key = 模板变量名，value = 数据）
     * 3. templateEngine.process("模板名", context) 渲染模板，输出 HTML 字符串
     *
     * 示例：
     * context.setVariable("projectCode", request.getProjectCode());
     * context.setVariable("handlerName", request.getHandlerName());
     *
     * 对应模板中写法：
     * <input type="text" th:value="${projectCode}">
     * <input type="text" th:value="${handlerName}">
     *
     * @param request 通知请求对象（包含 to/from/subject/projectCode/handlerName）
     * @return 填充后的 HTML 字符串
     */
    public String processTemplate(NotificationRequest request) {
        Context context = new Context();
        context.setVariable("projectCode", request.getProjectCode());
        context.setVariable("handlerName", request.getHandlerName());

        return templateEngine.process("bid-winning-notification", context);
    }
}