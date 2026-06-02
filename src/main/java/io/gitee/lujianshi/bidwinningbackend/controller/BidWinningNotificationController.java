package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.BidWinningMailRequest;
import io.gitee.lujianshi.bidwinningbackend.service.BidWinningMailSender;
import io.gitee.lujianshi.bidwinningbackend.service.BidWinningTemplateFiller;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中标通知书 Controller 层：路由 + 参数校验 + 调用 Service
 *
 * @RestController = @Controller + @ResponseBody
 *
 * 职责：
 * - 定义中标通知书发送路由（POST /send-notification）
 * - 接收请求参数（BidWinningMailRequest POJO，即 DTO）
 * - 调用 BidWinningTemplateFiller 渲染模板
 * - 调用 BidWinningMailSender 发送 HTML 邮件
 */
@RestController
public class BidWinningNotificationController {

    @Autowired private BidWinningTemplateFiller bidWinningTemplateFiller;

    @Autowired private BidWinningMailSender bidWinningMailSender;

    /**
     * 中标通知书发送接口
     *
     * 完整执行链路：
     * 1. 接收 JSON 请求（@RequestBody 将 JSON 反序列化为 BidWinningMailRequest）
     * 2. 调用 BidWinningTemplateFiller.processTemplate(request)
     *    ├─ 读取 request.getTemplateName() 作为模板文件名
     *    ├─ 调用 StaticResourceSearcher.validateTemplateExists(templateName) 验证模板存在
     *    │   （精确匹配 classpath:/templates/*.html，找不到抛异常，不 fallback）
     *    ├─ 构建 Thymeleaf Context，放入 projectCode / handlerName / bidWinningList
     *    └─ SpringTemplateEngine.process(templateName, context) 渲染模板
     * 3. 返回渲染后的 HTML 字符串 htmlContent
     * 4. 调用 BidWinningMailSender.sendHtmlEmail(to, subject, htmlContent) 发送邮件
     *
     * @param request 中标通知请求对象（BidWinningMailRequest extends PlainTextMailRequest）
     *                包含参数：
     *                - to：收件人邮箱
     *                - from：发件人邮箱（固定为 spring.mail.username，不使用请求传入值）
     *                - subject：邮件主题
     *                - projectCode：项目编号（模板填充变量）
     *                - handlerName：经办人（模板填充变量）
     *                - bidWinningList：List<BidWinningListItem>（th:each 循环渲染中标结果）
     *                - templateName：模板文件名（不含 .html 后缀，例如 "bid-winning-notification"）
     * @throws MessagingException 邮件发送失败时抛出
     * @throws IllegalArgumentException 模板文件不存在于 classpath:/templates/ 目录时抛出
     */
    @PostMapping("/send-notification")
    public void sendNotification(@RequestBody BidWinningMailRequest request) throws MessagingException {
        String htmlContent = bidWinningTemplateFiller.processTemplate(request);
        bidWinningMailSender.sendHtmlEmail(
                request.getTo(),
                request.getSubject(),
                htmlContent
        );
    }

}
