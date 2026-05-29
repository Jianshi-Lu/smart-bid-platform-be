package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.EmailRequest;
import io.gitee.lujianshi.bidwinningbackend.service.EmailService;
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
 */
@RestController
public class EmailController {

    @Autowired
    private EmailService emailService;

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
}