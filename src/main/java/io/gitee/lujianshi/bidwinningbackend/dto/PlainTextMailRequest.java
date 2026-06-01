package io.gitee.lujianshi.bidwinningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮件请求对象（DTO - Data Transfer Object）
 *
 * DTO：Data Transfer Object，数据传输对象
 * - 通用数据传输对象，可用于请求/响应/跨层传递
 * - 区别于 request/response 包：
 *   - request/：专用于接收前端/外部传入的参数
 *   - response/：专用于返回给前端/外部的数据
 *   - dto/：通用，可同时承担请求和响应职责
 *
 * 当前场景简单用 DTO 足够
 * 后续如需严格区分职责，可拆分为 request/EmailSendRequest + response/EmailSendResponse
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