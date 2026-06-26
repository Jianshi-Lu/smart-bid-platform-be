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

    /**
     * 服务存活探活。
     *
     * @return 固定字符串 "Service is up and running."
     */
    @GetMapping("/service-status")
    public String showStatus() {
        return "Service is up and running.";
    }

    /**
     * 发送纯文本邮件（同步）。
     *
     * @param request 邮件请求（to / subject / content 必填）
     * @return 成功消息
     */
    @PostMapping("/send-email")
    public String sendEmail(@RequestBody PlainTextMailRequest request) {
        mailService.sendEmail(request);
        return "Email sent successfully.";
    }

    /**
     * 异步发送中标通知书 HTML 邮件（延迟 5 秒），主线程立即返回"发送成功"。
     *
     * @param request 中标通知书邮件请求（含 bidWinningList 模板数据）
     * @return 成功消息
     */
    @PostMapping("/send-notification")
    public String sendNotification(@RequestBody BidWinningMailRequest request) {
        return statusCheckService.sendNotification(request);
    }
}
