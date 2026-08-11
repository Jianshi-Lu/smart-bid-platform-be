package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.statuscheck.BidWinningMailRequest;
import io.gitee.lujianshi.bidwinningbackend.utils.AsyncTaskManager;
import io.gitee.lujianshi.bidwinningbackend.utils.ResourceSearcher;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 服务状态检查与邮件测试 Service。
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
     * 异步发送中标通知书 HTML 邮件（延迟 5 秒）。主线程立即返回，邮件发送在后台子线程。
     *
     * @param request 含 to / subject / projectCode / handlerName / bidWinningList / templateName
     * @return "发送成功"（主线程立即返回）
     * @throws IllegalArgumentException 模板文件不存在时抛出
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
