package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.PlainTextMailRequest;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件服务层
 *
 * @Service = 标注为 Spring 容器中的 Bean
 *
 * 职责：
 * - 构建邮件消息
 * - 调用 JavaMailSender 发送邮件
 * - 发件人邮箱暂时固定为配置值（从 spring.mail.username 读取），后续可扩展为可配置
 *
 * 邮件发送完整链路（基于 PlainTextMailRequest DTO）：
 * 1. Controller 调用 service.sendEmail(request)，传入 PlainTextMailRequest 对象
 * 2. Service 用 request.getTo()、request.getSubject()、request.getContent() 获取数据
 * 3. Service 创建 SimpleMailMessage 对象，调用 setTo()、setSubject()、setText() 赋值
 * 4. Service 调用 mailSender.send(message) 发送
 * 5. JavaMailSender 使用已配置的 SMTP 信息（host/port/username/password）与邮件服务器通信
 * 6. 邮件服务器将邮件送达收件人
 *
 * 注：request.getFrom() 实际未使用，发件人固定为配置中的 spring.mail.username
 */
@Service
public class PlainTextMailSender {

    /**
     * @Resource JavaMailSender mailSender
     *
     * JavaMailSender 由 Spring Boot 自动配置（MailSenderAutoConfiguration）
     * - 读取 application.properties 的 spring.mail.* 配置项（host/port/username/password 等）
     * - 一次性设置到 JavaMailSenderImpl 实例
     * - send() 方法直接使用配置，不重复读取 properties
     *
     * 注意：注入的是一个已配置好的 JavaMailSenderImpl（JavaMailSender 接口的实现类）实例
     */
    @Resource
    private JavaMailSender mailSender;

    /**
     * @Value("${spring.mail.username}") senderEmail
     *
     * 发件人邮箱从配置读取（非硬编码）
     * Spring 启动时从 Environment 注入
     * 后续可改为其他配置项或数据库存储，实现发件人灵活配置
     */
    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * 发送邮件
     *
     * SimpleMailMessage：Spring 封装的纯文本邮件消息类
     * - 直接继承 Object，实现 MailMessage 接口
     * - 定义 from/to/subject/text 字段（set/get）
     *
     * mailSender.send()：使用已配置的 JavaMailSenderImpl 实例发送
     * - 此时 mailSender 内部已有 SMTP 配置，直接与邮件服务器通信
     * - 不需要再次读取 application.properties
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