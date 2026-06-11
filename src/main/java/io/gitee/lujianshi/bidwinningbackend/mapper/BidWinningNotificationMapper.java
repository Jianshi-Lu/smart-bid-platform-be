package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 定标通知 Mapper 接口 — 定义数据访问方法签名
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表层（sql/ddl/V4__create_bid_winning_notification.sql）
 *      ↓
 *   ② Entity 层（BidWinningNotification.java，Java ↔ DB 映射）
 *      ↓
 *   ③ Mapper 接口层（本类，只定义方法签名，无实现）
 *      ↓
 *   ④ Mapper XML 层（BidWinningNotificationMapper.xml，namespace 绑定本接口，SQL 实现）
 *      ↓
 *   ⑤ Service 层（注入本接口进行业务调用）
 *      ↓
 *   ⑥ DTO 层
 *      ↓
 *   ⑦ Controller 层
 * </pre>
 *
 * <h3>设计原则（与 BidHandlerInfoMapper 保持一致：手写、不继承 Plus）</h3>
 * <ul>
 *   <li>本接口<b>不继承</b> {@code BaseMapper<T>} —— 不使用 Plus 的自动 CRUD
 *       <ul>
 *         <li>好处：拓展性强，复杂 SQL 可在 XML 自由控制</li>
 *         <li>代价：每个方法都需在 XML 中写 SQL 映射</li>
 *       </ul>
 *   </li>
 *   <li>本接口<b>写</b> {@code @Mapper} 注解 —— 与 BidHandlerInfoMapper 风格保持一致
 *       <ul>
 *         <li>启动类 {@code @MapperScan("io.gitee.lujianshi.bidwinningbackend.mapper")} 已统一扫描</li>
 *         <li>本注解可省略但保留以提高 IDE 识别度</li>
 *       </ul>
 *   </li>
 *   <li>SQL 实现全部在 <b>resources/mapper/BidWinningNotificationMapper.xml</b> 中
 *       <ul>
 *         <li>namespace 必须等于本接口全限定名</li>
 *         <li>每个方法对应一个 SQL 标签（&lt;insert&gt; / &lt;update&gt; / &lt;delete&gt; / &lt;select&gt;）</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>对应数据库表 {@code bid_winning_notification}</h3>
 * <ul>
 *   <li>建表脚本：{@code src/main/resources/sql/ddl/V4__create_bid_winning_notification.sql}</li>
 *   <li>主键：{@code notification_id}（自增）</li>
 *   <li>外键：{@code handler_id} → {@code bid_handler_info.handler_id}（<b>逻辑外键</b>，DB 层无 FK 约束）</li>
 *   <li>字段数：19 个（含 6 个时间/金额/布尔字段）</li>
 *   <li>详见 {@code docs/database.md} §2.2</li>
 * </ul>
 *
 * <h3>方法命名规范</h3>
 * <ul>
 *   <li>{@code insert(entity)} — 插入，影响行数 1=成功</li>
 *   <li>{@code updateById(entity)} — 按主键更新，影响行数 1=成功</li>
 *   <li>{@code deleteById(id)} — 按主键删除，影响行数 1=成功</li>
 *   <li>{@code selectById(id)} — 按主键查询单条</li>
 *   <li>{@code selectAll()} — 查询全部（不传条件）</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification
 * @see io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService
 * @see docs/database.md
 */
@Mapper
public interface BidWinningNotificationMapper {

    /**
     * 插入一条定标通知记录
     *
     * 对应 XML：{@code <insert id="insert" useGeneratedKeys="true" keyProperty="notificationId">}
     * 自增 ID 会在执行后<b>回填</b>到入参 entity 的 {@code notificationId} 字段。
     * <p>
     * 注意：{@code biddingStatus} 字段在 XML INSERT 中被显式排除，依赖 DB 默认值 {@code '已完成'}。
     * {@code createdAt} 同样依赖 DB 默认值 {@code CURRENT_TIMESTAMP}。
     *
     * @param entity 待插入的实体（notificationId / biddingStatus / createdAt 可为 null，由 DB 默认值填充）
     * @return 影响行数（1=成功，0=失败）
     */
    int insert(BidWinningNotification entity);

    /**
     * 按主键更新定标通知
     *
     * 对应 XML：{@code <update id="updateById">}
     * <p>
     * 注意：{@code createdAt} 在 XML UPDATE 中被排除（创建时间不应被修改）。
     *
     * @param entity 待更新实体（必须携带 notificationId）
     * @return 影响行数（1=成功，0=记录不存在）
     */
    int updateById(BidWinningNotification entity);

    /**
     * 按主键删除定标通知
     *
     * 对应 XML：{@code <delete id="deleteById">}
     *
     * @param notificationId 主键 ID
     * @return 影响行数（1=成功，0=记录不存在）
     */
    int deleteById(Integer notificationId);

    /**
     * 按主键查询单条定标通知
     *
     * 对应 XML：{@code <select id="selectById" resultMap="BaseResultMap">}
     * XML 中 {@code <resultMap>} 定义 Java 字段 ↔ DB 列映射（notificationId ↔ notification_id 等）。
     *
     * @param notificationId 主键 ID
     * @return 实体（未找到返回 null）
     */
    BidWinningNotification selectById(Integer notificationId);

    /**
     * 查询全部定标通知
     *
     * 对应 XML：{@code <select id="selectAll" resultMap="BaseResultMap">}
     * 注意：当前未分页、未排序，数据量大时建议后续加分页 + 按 created_at DESC 排序。
     *
     * @return 全部定标通知列表（空表返回空 List 而非 null）
     */
    List<BidWinningNotification> selectAll();
}
