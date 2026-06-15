package io.gitee.lujianshi.bidwinningbackend.service.mail;

import io.gitee.lujianshi.bidwinningbackend.dto.mail.PlainTextMailRequest;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件服务层
 *
 * 职责：
 * - 构建邮件消息
 * - 调用 JavaMailSender 发送邮件
 * - 发件人邮箱固定为配置值（{@code spring.mail.username}），后续可扩展为可配置
 *
 * 注：request.getFrom() 实际未使用，发件人固定为配置中的 spring.mail.username
 */
@Service
public class PlainTextMailSender {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * 发送邮件
     *
     * @param request 邮件请求对象（to/subject/content）
     */
    public void sendEmail(PlainTextMailRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);  // 使用配置中的发件人邮箱，非 request.from
        message.setTo(request.getTo());
        message.setSubject(request.getSubject());
        message.setText(request.getContent());

        mailSender.send(message);
    }
}