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

/**
 * 服务状态 + 邮件测试 Controller。3 端点（service-status / send-email / send-notification）。
 */
@RestController
public class StatusCheckController {

    @Resource
    private MailService mailService;

    @Resource
    private StatusCheckService statusCheckService;

    @GetMapping("/service-status")
    public String showStatus() {
        return "Service is up and running.";
    }

    @PostMapping("/send-email")
    public String sendEmail(@RequestBody PlainTextMailRequest request) {
        mailService.sendEmail(request);
        return "Email sent successfully.";
    }

    /**
     * 异步发送中标通知书 HTML 邮件（延迟 5 秒），主线程立即返回"发送成功"。
     */
    @PostMapping("/send-notification")
    public String sendNotification(@RequestBody BidWinningMailRequest request) {
        return statusCheckService.sendNotification(request);
    }
}
