package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.PlainTextMailRequest;
import io.gitee.lujianshi.bidwinningbackend.service.PlainTextMailSender;
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
 * - 接收请求参数（PlainTextMailRequest POJO，即 DTO）
 * - 调用 PlainTextMailSender 发送邮件
 *
 * 注意：不写业务逻辑，只负责路由和调用 Service
 *
 * 邮件发送完整链路：
 * 1. Postman POST /send-email（form-data: to/subject/content）
 * 2. Spring 根据 @PostMapping 找到此 Controller
 * 3. Spring 自动将请求参数封装为 PlainTextMailRequest 对象（DTO）
 * 4. Controller 调用 service.sendEmail(request)
 * 5. 后续逻辑由 Service 层处理
 */
@RestController
public class PlainTextMailController {

    @Autowired private PlainTextMailSender plainTextMailSender;

    /**
     * @PostMapping("/send-email") 邮件发送接口
     *
     * 接收 PlainTextMailRequest（DTO 数据传输对象）
     * DTO：通用数据传输对象，可用于请求/响应/跨层传递
     * 当前场景简单用 DTO，后续可拆分为 request/response 包以区分职责
     *
     * @param request 邮件请求对象（to/from/subject/content）
     */
    @PostMapping("/send-email")
    public void sendEmail(PlainTextMailRequest request) {
        plainTextMailSender.sendEmail(request);
    }
}
