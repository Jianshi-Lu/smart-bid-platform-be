package io.gitee.lujianshi.bidwinningbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 经办人 / 招标负责人 — 数据库实体。对应表 {@code bid_handler_info}。
 *
 * <h3>字段 ↔ 列映射</h3>
 * <table border="1">
 *   <tr><th>Java 字段</th><th>DB 列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>handlerId</td><td>handler_id</td><td>INT, AUTO_INCREMENT, PK</td><td>主键，自增</td></tr>
 *   <tr><td>handlerName</td><td>handler_name</td><td>VARCHAR(50), NOT NULL</td><td>经办人姓名</td></tr>
 *   <tr><td>companyName</td><td>company_name</td><td>VARCHAR(100), NOT NULL (V2+)</td><td>所属采购公司</td></tr>
 *   <tr><td>departmentName</td><td>department_name</td><td>VARCHAR(100), NOT NULL (V2+)</td><td>所属采购部门</td></tr>
 *   <tr><td>targetEmail</td><td>target_email</td><td>VARCHAR(255)</td><td>通知邮件收件人（落库）</td></tr>
 * </table>
 *
 * <p>主键用 {@link Integer}（包装类）以区分 null 与 0。Lombok 三件套生成 getter/setter 与构造器。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid_handler_info")
public class BidHandlerInfo {

    @TableId(type = IdType.AUTO)
    private Integer handlerId;

    private String handlerName;

    private String companyName;

    private String departmentName;

    private String targetEmail;
}
