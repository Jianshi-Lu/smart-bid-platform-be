package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningSupplierDetail;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 中标结果明细 Mapper 接口 — 定义数据访问方法签名
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表层（sql/ddl/V5__create_bid_winning_supplier_detail.sql）
 *      ↓
 *   ② Entity 层（BidWinningSupplierDetail.java，Java ↔ DB 映射）
 *      ↓
 *   ③ Mapper 接口层（本类，只定义方法签名，无实现）
 *      ↓
 *   ④ Mapper XML 层（BidWinningSupplierDetailMapper.xml，namespace 绑定本接口，SQL 实现）
 *      ↓
 *   ⑤ Service 层（注入本接口进行业务调用）
 *      ↓
 *   ⑥ DTO 层
 *      ↓
 *   ⑦ Controller 层
 * </pre>
 *
 * <h3>设计原则（与 BidHandlerInfoMapper / BidWinningNotificationMapper 保持一致）</h3>
 * <ul>
 *   <li>本接口<b>不继承</b> {@code BaseMapper<T>} —— 不使用 Plus 的自动 CRUD
 *       <ul>
 *         <li>好处：拓展性强，复杂 SQL 可在 XML 自由控制</li>
 *         <li>代价：每个方法都需在 XML 中写 SQL 映射</li>
 *       </ul>
 *   </li>
 *   <li>本接口<b>写</b> {@code @Mapper} 注解 —— 与项目其他 Mapper 风格保持一致
 *       <ul>
 *         <li>启动类 {@code @MapperScan("io.gitee.lujianshi.bidwinningbackend.mapper")} 已统一扫描</li>
 *         <li>本注解可省略但保留以提高 IDE 识别度</li>
 *       </ul>
 *   </li>
 *   <li>SQL 实现全部在 <b>resources/mapper/BidWinningSupplierDetailMapper.xml</b> 中
 *       <ul>
 *         <li>namespace 必须等于本接口全限定名</li>
 *         <li>每个方法对应一个 SQL 标签（&lt;insert&gt; / &lt;update&gt; / &lt;delete&gt; / &lt;select&gt;）</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>对应数据库表 {@code bid_winning_supplier_detail}</h3>
 * <ul>
 *   <li>建表脚本：{@code src/main/resources/sql/ddl/V5__create_bid_winning_supplier_detail.sql}</li>
 *   <li>主键：{@code detail_id}（自增）</li>
 *   <li>外键：{@code notification_id} → {@code bid_winning_notification.notification_id}（<b>逻辑外键</b>，DB 层无 FK 约束）</li>
 *   <li>字段数：4 个（最简实现，无时间字段、无状态字段）</li>
 *   <li>业务关系：与 {@code bid_winning_notification} 是 1:N（一条定标通知 → 多条中标明细）</li>
 *   <li>详见 {@code docs/database.md} §2.3</li>
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
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidWinningSupplierDetail
 * @see io.gitee.lujianshi.bidwinningbackend.mapper.BidWinningNotificationMapper
 * @see docs/ database.md
 */
@Mapper
public interface BidWinningSupplierDetailMapper {

    /**
     * 插入一条中标结果明细记录
     *
     * 对应 XML：{@code <insert id="insert" useGeneratedKeys="true" keyProperty="detailId">}
     * 自增 ID 会在执行后<b>回填</b>到入参 entity 的 {@code detailId} 字段。
     *
     * @param entity 待插入的实体（detailId 可为 null，由 DB 自增生成；其余字段 NOT NULL 必须传值）
     * @return 影响行数（1=成功，0=失败）
     */
    int insert(BidWinningSupplierDetail entity);

    /**
     * 按主键更新中标结果明细
     *
     * 对应 XML：{@code <update id="updateById">}
     *
     * @param entity 待更新实体（必须携带 detailId）
     * @return 影响行数（1=成功，0=记录不存在）
     */
    int updateById(BidWinningSupplierDetail entity);

    /**
     * 按主键删除中标结果明细
     *
     * 对应 XML：{@code <delete id="deleteById">}
     *
     * @param detailId 主键 ID
     * @return 影响行数（1=成功，0=记录不存在）
     */
    int deleteById(Integer detailId);

    /**
     * 按主键查询单条中标结果明细
     *
     * 对应 XML：{@code <select id="selectById" resultMap="BaseResultMap">}
     * XML 中 {@code <resultMap>} 定义 Java 字段 ↔ DB 列映射（detailId ↔ detail_id 等）。
     *
     * @param detailId 主键 ID
     * @return 实体（未找到返回 null）
     */
    BidWinningSupplierDetail selectById(Integer detailId);

    /**
     * 查询全部中标结果明细
     *
     * 对应 XML：{@code <select id="selectAll" resultMap="BaseResultMap">}
     * 注意：当前未分页、未排序，数据量大时建议后续加分页 + 按 detail_id DESC 排序。
     *
     * @return 全部中标明细列表（空表返回空 List 而非 null）
     */
    List<BidWinningSupplierDetail> selectAll();
}
