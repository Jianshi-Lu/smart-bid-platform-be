package io.gitee.lujianshi.bidwinningbackend.service;

import org.springframework.stereotype.Service;

@Service
public class MailLoggingService {

    public String formatMailLog(String to, String subject, String htmlContent) {
        return """
            ## 邮件发送日志
            **收件人:** %s
            **主题:** %s
            ```html
            %s
            ```
            """.formatted(to, subject, htmlContent);
    }
}
