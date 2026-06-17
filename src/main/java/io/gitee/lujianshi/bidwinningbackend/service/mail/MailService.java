package io.gitee.lujianshi.bidwinningbackend.service.mail;

import io.gitee.lujianshi.bidwinningbackend.dto.mail.PlainTextMailRequest;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务 — 统一入口
 *
 * <p>扁平合并原 {@code PlainTextMailSender} + {@code BidWinningMailSender}，共享 {@code JavaMailSender}
 * 与发件人邮箱字段，避免两套 Sender 各自声明。</p>
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>{@link #sendEmail(PlainTextMailRequest)} — 纯文本邮件（原 {@code PlainTextMailSender.sendEmail}）</li>
 *   <li>{@link #sendHtmlEmail(String, String, String)} — HTML 邮件（原 {@code BidWinningMailSender.sendHtmlEmail}）</li>
 * </ul>
 *
 * <h3>共享字段</h3>
 * <ul>
 *   <li>{@link #mailSender}（JavaMailSender）— Spring SMTP 协议客户端（Spring Boot 自动配置 Bean，名 {@code mailSender}）</li>
 *   <li>{@link #senderEmail}（{@code spring.mail.username} 配置）— 发件人邮箱（DTO 中 {@code from} 字段保留但不使用）</li>
 * </ul>
 *
 * <h3>类名说明</h3>
 * <p>类名 {@code MailService} 而非 {@code MailSender}，避免与 Spring Boot 自动配置的
 * {@code JavaMailSender}（Bean 名 {@code mailSender}）同名冲突。</p>
 */
@Service
public class MailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * 发送纯文本邮件（{@code SimpleMailMessage}）
     *
     * @param request 邮件请求对象（to / subject / content；{@code from} 字段不使用，发件人固定走 {@code spring.mail.username}）
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
     * 发送 HTML 格式邮件（{@code MimeMessage} + {@code MimeMessageHelper}）
     *
     * @param to          收件人邮箱
     * @param subject     邮件主题
     * @param htmlContent 渲染后的 HTML 内容（Thymeleaf 模板填充结果）
     * @throws MessagingException 邮件发送失败时抛出
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
