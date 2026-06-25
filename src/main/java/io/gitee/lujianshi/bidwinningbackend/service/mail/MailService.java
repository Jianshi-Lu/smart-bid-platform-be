package io.gitee.lujianshi.bidwinningbackend.service.mail;

import io.gitee.lujianshi.bidwinningbackend.dto.statuscheck.PlainTextMailRequest;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务。类名 {@code MailService} 而非 {@code MailSender}，避免与 Spring Boot 自动配置的
 * {@code JavaMailSender}（Bean 名 {@code mailSender}）同名冲突。
 *
 * <p>{@code from} 字段保留但不使用，发件人固定走 {@code spring.mail.username}。</p>
 */
@Service
public class MailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * 发送纯文本邮件。
     *
     * @param request 邮件请求（{@code from} 字段不使用）
     */
    public void sendEmail(PlainTextMailRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(request.getTo());
        message.setSubject(request.getSubject());
        message.setText(request.getContent());

        mailSender.send(message);
    }

    /**
     * 发送 HTML 邮件。
     *
     * @param to          收件人
     * @param subject     主题
     * @param htmlContent HTML 内容（Thymeleaf 渲染结果）
     * @throws MessagingException SMTP 发送失败
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(senderEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
    }
}
