package io.gitee.lujianshi.bidwinningbackend.dto.statuscheck;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 纯文本邮件请求对象。{@code from} 字段保留但不使用，发件人固定走 {@code spring.mail.username}。
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
