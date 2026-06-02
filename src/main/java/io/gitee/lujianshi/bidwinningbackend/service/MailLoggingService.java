package io.gitee.lujianshi.bidwinningbackend.service;


import org.springframework.stereotype.Service;
import io.github.furstenheim.CopyDown;

/**
 * 邮件日志格式化服务*
 * 将邮件字段拼接为 Markdown 格式字符串，供异步任务打印到控制台。
 * 设计为独立方法，方便后续扩展日志输出目标（文件、数据库、消息队列等）。
 *
 * @see <a href="../../../docs/architecture.md#46-mailloggingservice">架构文档 4.6</a>
 */
@Service
public class MailLoggingService {

    /**
     * 格式化邮件日志为 Markdown 字符串
     *
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param htmlContent 渲染后的 HTML 内容
     * @return MD 格式日志字符串
     */

    public String formatMailLog(String to, String subject, String htmlContent) {
        CopyDown copyDown = new CopyDown();
        String md = copyDown.convert(htmlContent);

        return """
        ## 邮件发送日志
        **收件人:** %s
        **主题:** %s
        %s
        """.formatted(to, subject, md);
    }
}
