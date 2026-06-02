package io.gitee.lujianshi.bidwinningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 中标通知书邮件请求对象
 *
 * Lombok 注解说明：
 * - @Data：自动生成 getter/setter/equals/hashCode 基础方法
 * - @NoArgsConstructor：自动生成无参构造器，给 Spring/MyBatis/Jackson 等框架在反射创建对象、JSON 反序列化时使用
 * - @AllArgsConstructor：自动生成全参构造器，传参创建对象
 *
 * 字段说明：
 * - to / from / subject / content：继承自 PlainTextMailRequest（subject、content 本流程中不使用）
 * - projectCode：项目编号，用于填充 Thymeleaf 模板
 * - handlerName：经办人，用于填充 Thymeleaf 模板
 * - bidWinningList：中标结果列表，每项包含 supplierName（供应商名称）和 bidWinningAmount（中标价）
 * - templateName：模板文件名（不含 .html 后缀），由用户传入，后端根据此字段搜索并渲染对应模板
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BidWinningListItem {
        private String supplierName;
        private String bidWinningAmount;
    }
}