package io.gitee.lujianshi.bidwinningbackend.controller.mail;

import io.gitee.lujianshi.bidwinningbackend.dto.mail.PlainTextMailRequest;
import io.gitee.lujianshi.bidwinningbackend.service.mail.PlainTextMailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件 Controller 层：路由 + 参数校验 + 调用 Service
 *
 * 职责：
 * - 定义邮件发送路由（POST /send-email）
 * - 接收请求参数（PlainTextMailRequest）
 * - 调用 PlainTextMailSender 发送邮件
 */
@RestController
public class PlainTextMailController {

    @Autowired private PlainTextMailSender plainTextMailSender;

    /**
     * 邮件发送接口
     *
     * @param request 邮件请求对象（to/from/subject/content）
     */
    @PostMapping("/send-email")
    public void sendEmail(PlainTextMailRequest request) {
        plainTextMailSender.sendEmail(request);
    }
}
