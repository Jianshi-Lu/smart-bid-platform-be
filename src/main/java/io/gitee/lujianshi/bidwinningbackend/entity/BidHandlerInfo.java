package io.gitee.lujianshi.bidwinningbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 经办人 / 招标负责人 — 数据库实体（Entity）
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表层（sql/ddl/V1__create_handler_info.sql）
 *      ↓
 *   ② Entity 层（本类，Java ↔ DB 映射）
 *      ↓
 *   ③ Mapper 接口层（BidHandlerInfoMapper.java，定义方法签名）
 *      ↓
 *   ④ Mapper XML 层（BidHandlerInfoMapper.xml，SQL 实现）
 *      ↓
 *   ⑤ Service 层（BidHandlerInfoService.java，业务调用）
 *      ↓
 *   ⑥ DTO 层（BidHandlerInfoCreateRequest.java，请求载体）
 *      ↓
 *   ⑦ Controller 层（BidHandlerInfoController.java，HTTP 入口）
 * </pre>
 *
 * <h3>对应数据库表</h3>
 * <ul>
 *   <li>表名：{@code bid_handler_info}（由 V3 重命名自 handler_info）</li>
 *   <li>建表脚本：{@code src/main/resources/sql/ddl/V1__create_handler_info.sql}</li>
 *   <li>字段演进：V1（建表）→ V2（加 company_name / department_name）→ V3（重命名为 bid_handler_info）</li>
 * </ul>
 *
 * <h3>字段 ↔ 列映射</h3>
 * <table border="1">
 *   <tr><th>Java 字段</th><th>DB 列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>handlerId</td><td>handler_id</td><td>INT, AUTO_INCREMENT, PK</td><td>主键，自增</td></tr>
 *   <tr><td>handlerName</td><td>handler_name</td><td>VARCHAR(50), NOT NULL</td><td>经办人姓名</td></tr>
 *   <tr><td>companyName</td><td>company_name</td><td>VARCHAR(100), NOT NULL (V2+)</td><td>所属采购公司</td></tr>
 *   <tr><td>departmentName</td><td>department_name</td><td>VARCHAR(100), NOT NULL (V2+)</td><td>所属采购部门</td></tr>
 * </table>
 *
 * <h3>设计约定</h3>
 * <ul>
 *   <li>使用 Lombok {@code @Data} + {@code @NoArgsConstructor} + {@code @AllArgsConstructor} 三件套
 *       （无参构造器供 ORM 反射创建实例；全参构造器用于测试和快速构造）</li>
 *   <li>主键类型用 {@link Integer}（包装类）而非 {@code int}，区分 null 与 0</li>
 *   <li>{@link TableName} 显式声明映射表名（避免依赖 Plus 默认下划线转换）</li>
 *   <li>{@link TableId} 显式声明主键策略为 AUTO_INCREMENT</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper
 * @see io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService
 * @see docs/database.md
 */
@SuppressWarnings("ALL")
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
}
