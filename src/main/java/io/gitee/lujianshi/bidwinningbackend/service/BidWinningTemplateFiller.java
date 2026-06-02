package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.BidWinningMailRequest;
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
 * - 用 BidWinningMailRequest 的数据填充模板，生成最终 HTML 字符串
 * - 根据 request.templateName 动态选择模板文件，模板不存在则抛异常（不 fallback）
 *
 * 模板选择链路（完整执行流程）：
 * 1. Controller 调用 processTemplate(request)
 * 2. 从 request.getTemplateName() 取出模板文件名（不含 .html）
 * 3. 调用 StaticResourceSearcher.validateTemplateExists(templateName) 验证模板是否存在于 /templates/ 目录
 *    - 验证通过 → 继续
 *    - 验证失败 → 抛出 IllegalArgumentException，不 fallback，不使用默认模板
 * 4. 构建 Thymeleaf Context，放入 projectCode / handlerName / bidWinningList
 * 5. 调用 SpringTemplateEngine.process(templateName, context) 渲染模板
 *    - templateName 直接作为模板名（不含 .html），由 TemplateResolver 拼接完整路径
 *    - 例如：templateName = "bid-winning-notification2"
 *      → TemplateResolver 拼接为 classpath:/templates/bid-winning-notification2.html
 * 6. 返回渲染后的 HTML 字符串
 *
 * 模板搜索说明（StaticResourceSearcher）：
 * - 使用 PathMatchingResourcePatternResolver 扫描 classpath:/templates/*.html
 * - 精确匹配：传入 "bid-winning-notification2"，精确匹配文件名，不使用通配符或模糊匹配
 * - 行为安全：不自动 fallback，不存在则抛异常
 *
 * Spring Boot 配置（ThymeleafAutoConfiguration）：
 * - 默认路径：classpath:/templates/
 * - 实际读取 classpath:/templates/{templateName}.html
 */
@Service
public class BidWinningTemplateFiller {

    @jakarta.annotation.Resource
    private SpringTemplateEngine templateEngine;

    @jakarta.annotation.Resource
    private StaticResourceSearcher staticResourceSearcher;

    /**
     * 处理模板：用 BidWinningMailRequest 数据填充 Thymeleaf 模板
     *
     * Context 包说明（org.thymeleaf.context）：
     * - Context 是 Thymeleaf 的"模板数据容器"，封装了一个 Map + 一个 Locale
     * - 模板引擎渲染时，遇到 ${变量名} → 调用 context.getVariable("变量名")
     * - 底层使用 OGNL/SpringEL 表达式解析，从 Map 里取值并填充到 HTML
     *
     * 模板选择流程：
     * 1. 读取 request.getTemplateName() 作为模板文件名（不含 .html 后缀）
     * 2. staticResourceSearcher.validateTemplateExists(templateName)
     *    - 扫描 classpath:/templates/*.html 精确匹配
     *    - 找不到则抛出 IllegalArgumentException，不 fallback
     * 3. 模板文件存在后，由 TemplateResolver 拼接完整路径：
     *    templateName = "bid-winning-notification2"
     *    → classpath:/templates/bid-winning-notification2.html
     *
     * @param request 通知请求对象
     *                 包含参数：
     *                 - to / from / subject：继承自 PlainTextMailRequest
     *                 - projectCode：项目编号（模板填充变量）
     *                 - handlerName：经办人（模板填充变量）
     *                 - bidWinningList：List<BidWinningListItem>（th:each 循环渲染）
     *                 - templateName：模板文件名（不含 .html 后缀，用于动态选择模板）
     * @return 填充后的 HTML 字符串
     * @throws IllegalArgumentException 模板文件在 classpath:/templates/ 目录下不存在时抛出
     */
    public String processTemplate(BidWinningMailRequest request) {
        String templateName = request.getTemplateName();
        staticResourceSearcher.validateTemplateExists(templateName);

        Context context = new Context();
        context.setVariable("projectCode", request.getProjectCode());
        context.setVariable("handlerName", request.getHandlerName());
        context.setVariable("bidWinningList", request.getBidWinningList());

        return templateEngine.process(templateName, context);
    }
}