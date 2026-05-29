package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.EmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// * 职责：
// * - 构建邮件消息
// * - 调用 JavaMailSender 发送邮件

@Service
public class EmailService {

    // 实例化以使用JavaMailSender类的方法send()
    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendEmail(EmailRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(request.getTo());
        message.setSubject(request.getSubject());
        message.setText(request.getContent());

        mailSender.send(message);
    }
}