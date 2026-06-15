package io.gitee.lujianshi.bidwinningbackend.dto.mail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮件请求对象（DTO - Data Transfer Object）
 *
 * 字段说明：
 * - to：收件人邮箱地址
 * - from：发件人邮箱地址（暂时无用，发件人从配置 spring.mail.username 读取）
 * - subject：邮件主题
 * - content：邮件内容
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlainTextMailRequest {
    private String to;
    private String from;
    private String subject;
    private String content;
}