package io.gitee.lujianshi.bidwinningbackend.service.mail;

import io.gitee.lujianshi.bidwinningbackend.dto.mail.BidWinningMailRequest;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Thymeleaf 模板解析服务
 *
 * 功能：
 * - 读取 src/main/resources/templates/ 下的 Thymeleaf 模板
 * - 用 BidWinningMailRequest 的数据填充模板，生成最终 HTML 字符串
 * - 根据 request.templateName 动态选择模板文件，模板不存在则抛异常（不 fallback）
 *
 * 模板选择链路（完整执行流程）：
 * 1. Controller 调用 processTemplate(request)
 * 2. 从 request.getTemplateName() 取出模板文件名（不含 .html）
 * 3. 调用 ResourceSearcher.validateTemplateExists(templateName)
 *    - 验证通过 → 继续
 *    - 验证失败 → 抛出 IllegalArgumentException，不 fallback，不使用默认模板
 * 4. 构建 Thymeleaf Context，放入 projectCode / handlerName / bidWinningList
 * 5. 调用 SpringTemplateEngine.process(templateName, context) 渲染模板
 * 6. 返回渲染后的 HTML 字符串
 */
@Service
public class TemplateFiller {

    @jakarta.annotation.Resource
    private SpringTemplateEngine templateEngine;

    @jakarta.annotation.Resource
    private ResourceSearcher staticResourceSearcher;

    /**
     * 处理模板：用 BidWinningMailRequest 数据填充 Thymeleaf 模板
     *
     * @param request 通知请求对象
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