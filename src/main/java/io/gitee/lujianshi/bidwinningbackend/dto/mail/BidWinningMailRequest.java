package io.gitee.lujianshi.bidwinningbackend.dto.mail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 中标通知书邮件请求对象
 *
 * 字段说明：
 * - to / from / subject / content：继承自 PlainTextMailRequest
 * - projectCode：项目编号，用于填充 Thymeleaf 模板
 * - handlerName：经办人，用于填充 Thymeleaf 模板
 * - bidWinningList：中标结果列表
 * - templateName：模板文件名（不含 .html 后缀）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class BidWinningMailRequest extends PlainTextMailRequest {
    //    PlainTextMailRequest:
    //    private String to;
    //    private String from; （properties）
    //    private String subject;
    //    private String content;
    private String projectCode;  // 项目编号
    private String handlerName;       // 经办人
    private List<BidWinningListItem> bidWinningList;    // 中标结果表格
    private String templateName;      // 模板文件名（不含 .html 后缀）
}