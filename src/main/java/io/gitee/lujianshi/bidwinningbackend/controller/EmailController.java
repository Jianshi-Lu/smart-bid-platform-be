package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.EmailRequest;
import io.gitee.lujianshi.bidwinningbackend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件 Controller 层：路由 + 参数校验 + 调用 Service
 *
 * @RestController = @Controller + @ResponseBody
 *
 * 职责：
 * - 定义邮件发送路由（POST /send-email）
 * - 接收请求参数
 * - 调用 EmailService 发送邮件
 */
@RestController
public class EmailController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/send-email")
    public void sendEmail(EmailRequest request) {
        emailService.sendEmail(request);
    }
}