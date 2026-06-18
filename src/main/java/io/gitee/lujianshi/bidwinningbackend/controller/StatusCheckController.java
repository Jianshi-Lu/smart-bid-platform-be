package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.statuscheck.BidWinningMailRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.statuscheck.PlainTextMailRequest;
import io.gitee.lujianshi.bidwinningbackend.service.mail.MailService;
import io.gitee.lujianshi.bidwinningbackend.service.statuscheck.StatusCheckService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusCheckController {

    @Resource
    private MailService mailService;

    @Resource
    private StatusCheckService statusCheckService;

    /**
     * <h2>服务状态探活</h2>
     *
     * <h3>请求</h3>
     * <pre>curl http://localhost:8080/service-status</pre>
     *
     * <h3>响应</h3>
     * <pre>Service is up and running.</pre>
     */
    @GetMapping("/service-status")
    public String showStatus() {
        return "Service is up and running.";
    }

    /**
     * <h2>发送纯文本测试邮件</h2>
     *
     * <p>用于验证 SMTP 链路通畅（与 /service-status 配对的"实测"端点）。</p>
     *
     * <h3>输入参数（JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code to}</td><td>String</td><td>是</td><td>收件人邮箱</td></tr>
     *   <tr><td>{@code from}</td><td>String</td><td>否</td><td><b>实际无效</b>，发件人固定从配置 {@code spring.mail.username} 读取</td></tr>
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
     *
     * @param request 邮件请求 DTO（{@link PlainTextMailRequest}）
     */
    @PostMapping("/send-email")
    public String sendEmail(@RequestBody PlainTextMailRequest request) {
        mailService.sendEmail(request);
        return "Email sent successfully.";
    }

    /**
     * <h2>发送中标通知书 HTML 邮件</h2>
     *
     * <p>异步发送（延迟 5 秒），主线程立即返回"发送成功"。</p>
     *
     * <h3>输入参数（JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code to}</td><td>String</td><td>是</td><td>收件人邮箱</td></tr>
     *   <tr><td>{@code subject}</td><td>String</td><td>是</td><td>邮件主题</td></tr>
     *   <tr><td>{@code projectCode}</td><td>String</td><td>是</td><td>项目编号（模板填充变量）</td></tr>
     *   <tr><td>{@code handlerName}</td><td>String</td><td>是</td><td>经办人姓名（模板填充变量）</td></tr>
     *   <tr><td>{@code bidWinningList}</td><td>List&lt;BidWinningListItem&gt;</td><td>是</td><td>中标结果列表（th:each 循环渲染）</td></tr>
     *   <tr><td>{@code templateName}</td><td>String</td><td>是</td><td>模板文件名，不含 .html 后缀</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/send-notification \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "to":"bid@example.com",
     *     "subject":"XX项目中标通知书",
     *     "projectCode":"PROJ20260617",
     *     "handlerName":"张三",
     *     "templateName":"bid-winning-notification",
     *     "bidWinningList":[
     *       {"supplierName":"A供应商","bidWinningAmount":"120000"},
     *       {"supplierName":"B供应商","bidWinningAmount":"135000"}
     *     ]
     *   }'
     * </pre>
     *
     * @param request 邮件请求 DTO（{@link BidWinningMailRequest}）
     */
    @PostMapping("/send-notification")
    public String sendNotification(@RequestBody BidWinningMailRequest request) {
        return statusCheckService.sendNotification(request);
    }
}
