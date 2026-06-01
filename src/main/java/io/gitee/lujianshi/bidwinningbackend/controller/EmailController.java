package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.EmailRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.NotificationRequest;
import io.gitee.lujianshi.bidwinningbackend.service.EmailService;
import io.gitee.lujianshi.bidwinningbackend.service.NotificationService;
import io.gitee.lujianshi.bidwinningbackend.service.TemplateService;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件 Controller 层：路由 + 参数校验 + 调用 Service
 *
 * @RestController = @Controller + @ResponseBody
 *
 * 职责：
 * - 定义邮件发送路由（POST /send-email）
 * - 接收请求参数（EmailRequest POJO，即 DTO）
 * - 调用 EmailService 发送邮件
 *
 * 注意：不写业务逻辑，只负责路由和调用 Service
 *
 * 邮件发送完整链路：
 * 1. Postman POST /send-email（form-data: to/subject/content）
 * 2. Spring 根据 @PostMapping 找到此 Controller
 * 3. Spring 自动将请求参数封装为 EmailRequest 对象（DTO）
 * 4. Controller 调用 service.sendEmail(request)
 * 5. 后续逻辑由 Service 层处理
 */
@RestController
public class EmailController {

    @Autowired private EmailService emailService;

    @Autowired private TemplateService templateService;

    @Autowired private NotificationService NotificationService;

    /**
     * @PostMapping("/send-email") 邮件发送接口
     *
     * 接收 EmailRequest（DTO 数据传输对象）
     * DTO：通用数据传输对象，可用于请求/响应/跨层传递
     * 当前场景简单用 DTO，后续可拆分为 request/response 包以区分职责
     *
     * @param request 邮件请求对象（to/from/subject/content）
     */
    @PostMapping("/send-email")
    public void sendEmail(EmailRequest request) {
        emailService.sendEmail(request);
    }

    /**
     * @PostMapping("/send-notification") 中标通知书发送接口
     *
     * 复用设计：NotificationRequest extends EmailRequest
     * - 继承 EmailRequest 的 to/from/subject 字段
     * - 新增 projectCode（项目编号）、handlerName（经办人）字段
     *
     * 处理流程：
     * 1. Postman 发送 POST /send-notification（form-data: to/from/subject/projectCode/handlerName）
     * 2. Spring 自动将请求参数绑定到 NotificationRequest（继承 EmailRequest）
     * 3. TemplateService.processTemplate(request) 使用 projectCode + handlerName 填充 Thymeleaf 模板
     * 4. 将生成的 HTML 字符串设置为 EmailRequest.content
     * 5. EmailService.sendEmail(request) 发送邮件
     *
     * @param to 收件人邮箱
     * @param subject 邮件主题（建议：中标通知书）
     * @param projectCode 项目编号（模板填充）
     * @param handlerName 经办人（模板填充）
     *
     * 注意：subject 固定为"中标通知书"，邮件内容由 Thymeleaf 模板自动生成
     *
     * @param request 通知请求对象（NotificationRequest extends EmailRequest）
     */
    @PostMapping("/send-notification")
    public void sendNotification(NotificationRequest request) throws MessagingException {
        // 1. 组装 模板 + projectCode， handlerName 生成 HTML链接
        String htmlContent = templateService.processTemplate(request);

        // 2. 调用 NotificationService（支持 HTML）
        NotificationService.sendHtmlEmail(
                request.getTo(),
                request.getSubject(),
                htmlContent
        );
    }

}