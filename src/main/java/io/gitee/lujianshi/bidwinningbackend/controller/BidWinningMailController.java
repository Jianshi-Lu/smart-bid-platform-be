package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.BidWinningMailRequest;
import io.gitee.lujianshi.bidwinningbackend.service.BidWinningMailSender;
import io.gitee.lujianshi.bidwinningbackend.service.BidWinningTemplateFiller;
import io.gitee.lujianshi.bidwinningbackend.service.MailLoggingService;
import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
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
 * - 调用 MailLoggingService 打印 MD 格式日志（异步任务内）
 * - 调用 BidWinningMailSender 发送 HTML 邮件（异步延迟5秒）
 */
@RestController
public class BidWinningMailController {

    @Autowired private BidWinningTemplateFiller bidWinningTemplateFiller;

    @Autowired private BidWinningMailSender bidWinningMailSender;

    @Autowired private MailLoggingService mailLoggingService;

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
     * 4. 立即返回 "发送成功"（主线程不受异步任务影响）
     * 5. 异步任务（延迟5秒后执行）：
     *    ├─ 调用 MailLoggingService.formatMailLog() 打印 MD 格式日志到控制台
     *    └─ 调用 BidWinningMailSender.sendHtmlEmail(to, subject, htmlContent) 发送邮件
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
     * @throws IllegalArgumentException 模板文件不存在于 classpath:/templates/ 目录时抛出
     * @return 字符串 "发送成功"（立即返回，不等待邮件实际发送）
     *
     * TODO: 少参（templateName）报错-需新增兜底机制
     */
    @PostMapping("/send-notification")
    public String sendNotification(@RequestBody BidWinningMailRequest request) {
        String htmlContent = bidWinningTemplateFiller.processTemplate(request);

        AsyncTaskManager.execute(() -> {
            System.out.println(mailLoggingService.formatMailLog(
                request.getTo(), request.getSubject(), htmlContent));

            try {
                bidWinningMailSender.sendHtmlEmail(
                    request.getTo(),
                    request.getSubject(),
                    htmlContent
                );
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }, 5000);
        return "发送成功";
    }
}
