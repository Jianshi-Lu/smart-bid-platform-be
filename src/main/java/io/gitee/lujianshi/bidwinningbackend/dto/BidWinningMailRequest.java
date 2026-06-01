package io.gitee.lujianshi.bidwinningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目信息实体类
 *
 * Lombok 注解说明：
 * - @Data：自动生成 getter/setter/equals/hashCode 基础方法
 * - @NoArgsConstructor：自动生成无参构造器，给 Spring/MyBatis/Jackson 等框架在反射创建对象、JSON 反序列化时使用
 * - @AllArgsConstructor：自动生成全参构造器，传参创建对象
 *
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
}