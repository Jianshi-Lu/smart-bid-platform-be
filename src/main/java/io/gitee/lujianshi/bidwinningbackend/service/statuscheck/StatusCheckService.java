package io.gitee.lujianshi.bidwinningbackend.service.statuscheck;

import io.gitee.lujianshi.bidwinningbackend.dto.statuscheck.BidWinningMailRequest;
import io.gitee.lujianshi.bidwinningbackend.service.mail.MailService;
import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
import io.gitee.lujianshi.bidwinningbackend.utils.ResourceSearcher;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 服务状态检查与邮件测试 Service。
 *
 * <p>本类的职责：
 * <ul>
 *   <li>{@link #sendNotification(BidWinningMailRequest)} — 中标通知书 HTML 邮件发送（异步，延迟 5 秒）</li>
 * </ul>
 *
 * <p>遵循与其他业务 Service（{@code BidHandlerInfoService} / {@code BidWinningNotificationService}
 * 相同的架构模式：Controller 注入 Service，Service 内部编排 MailService / Thymeleaf / ResourceSearcher。
 *
 * <h3>调用链路</h3>
 * <pre>
 *   StatusCheckController
 *     → StatusCheckService.sendNotification(request)
 *       → ResourceSearcher.validateTemplateExists()
 *       → SpringTemplateEngine.process(templateName, context)
 *       → AsyncTaskManager.execute(..., 5000)
 *         → MailService.sendHtmlEmail()
 * </pre>
 */
@Service
public class StatusCheckService {

    @Resource
    private SpringTemplateEngine templateEngine;

    @Resource
    private ResourceSearcher resourceSearcher;

    @Resource
    private MailService mailService;

    /**
     * 发送中标通知书 HTML 邮件（异步，延迟 5 秒）。
     *
     * <p>主线程立即返回"发送成功"，不等待实际 SMTP 发送完成。
     * 邮件发送在后台子线程执行，失败仅打印日志，不抛异常。</p>
     *
     * @param request 含 to / subject / projectCode / handlerName / bidWinningList / templateName
     * @return "发送成功"（主线程立即返回）
     * @throws IllegalArgumentException 模板文件在 classpath:/templates/ 下不存在时抛出
     */
    public String sendNotification(BidWinningMailRequest request) {
        String templateName = request.getTemplateName();
        resourceSearcher.validateTemplateExists(templateName);

        Context context = new Context();
        context.setVariable("projectCode", request.getProjectCode());
        context.setVariable("handlerName", request.getHandlerName());
        context.setVariable("bidWinningList", request.getBidWinningList());

        String htmlContent = templateEngine.process(templateName, context);

        AsyncTaskManager.execute(() -> {
            try {
                mailService.sendHtmlEmail(
                        request.getTo(),
                        request.getSubject(),
                        htmlContent
                );
            } catch (MessagingException e) {
                System.err.println("[STATUS-CHECK] 邮件发送失败: " + e.getMessage());
            } finally {
                System.out.println("邮件发送任务完成: to=" + request.getTo());
            }
        }, 5000);

        return "发送成功";
    }
}
