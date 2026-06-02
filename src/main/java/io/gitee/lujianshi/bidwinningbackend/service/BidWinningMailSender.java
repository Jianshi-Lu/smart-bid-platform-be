package io.gitee.lujianshi.bidwinningbackend.service;

import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * HTML 邮件发送服务
 *
 * 使用 MimeMessage + MimeMessageHelper 构造并发送 HTML 格式邮件。
 * 与 PlainTextMailSender（SimpleMailMessage）的区别：
 * - SimpleMailMessage 仅支持纯文本，MimeMessage 支持 HTML/附件/多语言编码
 * - 中标通知书含表格样式，必须使用 HTML 邮件
 *
 * @see <a href="../../../docs/architecture.md#44-bidwinningmailsender">架构文档 4.4</a>
 */
@Service
public class BidWinningMailSender
{
    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}") private String senderEmail;

    /**
     * 发送 HTML 格式邮件
     *
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param htmlContent 渲染后的 HTML 内容（Thymeleaf 模板填充结果）
     * @throws MessagingException 邮件发送失败时抛出
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(senderEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);  // HTML 格式

        mailSender.send(mimeMessage);
    }
}
