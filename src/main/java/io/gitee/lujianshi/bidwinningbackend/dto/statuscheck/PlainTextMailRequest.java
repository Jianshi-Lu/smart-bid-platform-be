package io.gitee.lujianshi.bidwinningbackend.dto.statuscheck;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 纯文本邮件请求对象。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code to} — 收件人邮箱地址</li>
 *   <li>{@code from} — 发件人邮箱地址（暂时无用，发件人从配置 {@code spring.mail.username} 读取）</li>
 *   <li>{@code subject} — 邮件主题</li>
 *   <li>{@code content} — 邮件内容（纯文本，不支持 HTML）</li>
 * </ul>
 *
 * <p>使用方：
 * <ul>
 *   <li>{@code StatusCheckService.sendEmail(PlainTextMailRequest)}</li>
 *   <li>{@code StatusCheckController.sendEmail} 端点</li>
 * </ul>
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
