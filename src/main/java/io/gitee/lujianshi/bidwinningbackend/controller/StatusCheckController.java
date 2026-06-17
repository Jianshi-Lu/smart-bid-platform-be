package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.mail.BidWinningMailRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.mail.PlainTextMailRequest;
import io.gitee.lujianshi.bidwinningbackend.utils.TemplateFiller;
import io.gitee.lujianshi.bidwinningbackend.service.mail.MailService;
import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusCheckController {
    @GetMapping("/service-status")
    public String showStatus() {
        return "Service is up and running.";
    }

    @Resource
    private MailService mailService;
    /**
     * <h2>发送纯文本测试邮件</h2>
     * 用于验证 SMTP 链路通畅（与 /service-status 配对的"实测"端点）
     *
     *
     * <h3>输入参数（JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code to}</td><td>String</td><td>是</td><td>收件人邮箱（如 "alice@example.com"）</td></tr>
     *   <tr><td>{@code from}</td><td>String</td><td>否</td><td><b>实际无效</b>，发件人固定从配置 {@code spring.mail.username} 读取，本字段保留仅为兼容</td></tr>
     *   <tr><td>{@code subject}</td><td>String</td><td>是</td><td>邮件主题</td></tr>
     *   <tr><td>{@code content}</td><td>String</td><td>是</td><td>邮件正文（纯文本，不支持 HTML）</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/send-email \
     *   -H "Content-Type: application/json" \
     *   -d '{"to":"alice@example.com","subject":"测试","content":"Hello"}'
     * </pre>
     * @param request 邮件请求 DTO（{@link PlainTextMailRequest}）
     */
    @PostMapping("/send-email")
    public String sendEmail(@RequestBody PlainTextMailRequest request) {
        mailService.sendEmail(request);
        return "Email sent successfully.";
    }


    @Resource
    private TemplateFiller templateFiller;
    /**
     * <h2>中标通知书发送接口<h2/>
     *
     * <h3>输入参数（JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code to}</td><td>String</td><td>是</td><td>收件人邮箱</td></tr>
     *   <tr><td>{@code subject}</td><td>String</td><td>是</td><td>邮件主题</td></tr>
     *   <tr><td>{@code projectCode}</td><td>String</td><td>是</td><td>项目编号，用于模板填充变量</td></tr>
     *   <tr><td>{@code handlerName}</td><td>String</td><td>是</td><td>经办人，用于模板填充变量</td></tr>
     *   <tr><td>{@code bidWinningList}</td><td>List&lt;BidWinningListItem&gt;</td><td>是</td><td>中标结果列表，页面使用 th:each 循环渲染</td></tr>
     *   <tr><td>{@code templateName}</td><td>String</td><td>是</td><td>模板文件名，不含 .html 后缀，例如 "bid-winning-notification"</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/send-notification \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "to":"bid@example.com",
     *     "from":"ignore@demo.com",
     *     "subject":"XX项目中标通知书",
     *     "projectCode":"PROJ20260617",
     *     "handlerName":"张三",
     *     "templateName":"bid-winning-notification",
     *     "bidWinningList":[
     *       {"supplierName":"A供应商","bidPrice":"120000","winFlag":true},
     *       {"supplierName":"B供应商","bidPrice":"135000","winFlag":false}
     *     ]
     *   }'
     * </pre>
     * @param request 邮件请求 DTO（{@link BidWinningMailRequest}）
     */
    @PostMapping("/send-notification")
    public String sendNotification(@RequestBody BidWinningMailRequest request) {
        String htmlContent = templateFiller.processTemplate(request);

        AsyncTaskManager.execute(() -> {
            try {
                mailService.sendHtmlEmail(
                        request.getTo(),
                        request.getSubject(),
                        htmlContent
                );
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            } finally {
                System.out.println("邮件发送任务完成: to=" + request.getTo());
            }
        }, 5000);
        return "发送成功";
    }
}
