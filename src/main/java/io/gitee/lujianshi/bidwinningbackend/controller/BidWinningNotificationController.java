package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.BidWinningMailRequest;
import io.gitee.lujianshi.bidwinningbackend.service.BidWinningMailSender;
import io.gitee.lujianshi.bidwinningbackend.service.BidWinningTemplateFiller;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 */
@RestController
public class BidWinningNotificationController {

    @Autowired private BidWinningTemplateFiller bidWinningTemplateFiller;

    @Autowired private BidWinningMailSender bidWinningMailSender;

    /**
     * 中标通知书发送接口
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
     * @param request 中标通知请求对象（BidWinningMailRequest extends PlainTextMailRequest）
     *                包含参数：to（收件人邮箱）、固定*from（发件人邮箱）、subject（邮件主题）、
     *                projectCode（项目编号）、handlerName（经办人）、bidWinningList（List<BidWinningListItem>，中标结果列表）
     */
    @PostMapping("/send-notification")
    public void sendNotification(@RequestBody BidWinningMailRequest request) throws MessagingException {
        // 1. 组装 模板 + projectCode， handlerName 生成 HTML链接
        String htmlContent = bidWinningTemplateFiller.processTemplate(request);

        // 2. 调用 BidWinningMailSender发送拼接后的.html文件
        bidWinningMailSender.sendHtmlEmail(
                request.getTo(),
                request.getSubject(),
                htmlContent
        );
    }

}
