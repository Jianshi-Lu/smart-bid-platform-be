package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.BidWinningMailRequest;
import io.gitee.lujianshi.bidwinningbackend.service.BidWinningMailSender;
import io.gitee.lujianshi.bidwinningbackend.service.BidWinningTemplateFiller;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中标通知书 Controller 层：路由 + 参数校验 + 调用 Service
 *
 * @RestController = @Controller + @ResponseBody
 *
 * 职责：
 * - 定义中标通知书发送路由（POST /send-notification）
 * - 接收请求参数（BidWinningMailRequest POJO，即 DTO）
 * - 调用 BidWinningTemplateFiller 渲染模板
 * - 调用 BidWinningMailSender 发送 HTML 邮件
 *
 * 注意：不写业务逻辑，只负责路由和调用 Service
 *
 * 处理流程：
 * 1. Postman 发送 POST /send-notification（form-data: to/from/subject/projectCode/handlerName）
 * 2. Spring 自动将请求参数绑定到 BidWinningMailRequest（继承 PlainTextMailRequest）
 * 3. BidWinningTemplateFiller.processTemplate(request) 使用 projectCode + handlerName 填充 Thymeleaf 模板
 * 4. 将生成的 HTML 字符串设置为 PlainTextMailRequest.content
 * 5. BidWinningMailSender.sendHtmlEmail(request) 发送邮件
 *
 * @param to 收件人邮箱
 * @param subject 邮件主题（建议：中标通知书）
 * @param projectCode 项目编号（模板填充）
 * @param handlerName 经办人（模板填充）
 *
 * 注意：subject 固定为"中标通知书"，邮件内容由 Thymeleaf 模板自动生成
 *
 * @param request 通知请求对象（BidWinningMailRequest extends PlainTextMailRequest）
 */
@RestController
public class BidWinningNotificationController {

    @Autowired private BidWinningTemplateFiller bidWinningTemplateFiller;

    @Autowired private BidWinningMailSender bidWinningMailSender;

    /**
     * @PostMapping("/send-notification") 中标通知书发送接口
     *
     * 复用设计：BidWinningMailRequest extends PlainTextMailRequest
     * - 继承 PlainTextMailRequest 的 to/from/subject 字段
     * - 新增 projectCode（项目编号）、handlerName（经办人）字段
     *
     * 处理流程：
     * 1. Postman 发送 POST /send-notification（form-data: to/from/subject/projectCode/handlerName）
     * 2. Spring 自动将请求参数绑定到 BidWinningMailRequest（继承 PlainTextMailRequest）
     * 3. BidWinningTemplateFiller.processTemplate(request) 使用 projectCode + handlerName 填充 Thymeleaf 模板
     * 4. 将生成的 HTML 字符串设置为 PlainTextMailRequest.content
     * 5. PlainTextMailSender.sendEmail(request) 发送邮件
     *
     * @param to 收件人邮箱
     * @param subject 邮件主题（建议：中标通知书）
     * @param projectCode 项目编号（模板填充）
     * @param handlerName 经办人（模板填充）
     *
     * 注意：subject 固定为"中标通知书"，邮件内容由 Thymeleaf 模板自动生成
     *
     * @param request 通知请求对象（BidWinningMailRequest extends PlainTextMailRequest）
     */
    @PostMapping("/send-notification")
    public void sendNotification(BidWinningMailRequest request) throws MessagingException {
        // 1. 组装 模板 + projectCode， handlerName 生成 HTML链接
        String htmlContent = bidWinningTemplateFiller.processTemplate(request);

        // 2. 调用 BidWinningMailSender（支持 HTML）
        bidWinningMailSender.sendHtmlEmail(
                request.getTo(),
                request.getSubject(),
                htmlContent
        );
    }

}
