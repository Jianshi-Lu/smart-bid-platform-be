package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 经办人 Mapper 接口 — 定义数据访问方法签名
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表层（sql/ddl/V1__create_handler_info.sql）
 *      ↓
 *   ② Entity 层（BidHandlerInfo.java，Java ↔ DB 映射）
 *      ↓
 *   ③ Mapper 接口层（本类，只定义方法签名，无实现）
 *      ↓
 *   ④ Mapper XML 层（BidHandlerInfoMapper.xml，namespace 绑定本接口，SQL 实现）
 *      ↓
 *   ⑤ Service 层（注入本接口进行业务调用）
 *      ↓
 *   ⑥ DTO 层
 *      ↓
 *   ⑦ Controller 层
 * </pre>
 *
 * <h3>设计原则（坚持 Java 与 SQL 完全分离）</h3>
 * <ul>
 *   <li>本接口<b>不继承</b> {@code BaseMapper<T>} —— 不使用 Plus 的自动 CRUD
 *       <ul>
 *         <li>好处：拓展性强，复杂 SQL 可在 XML 自由控制</li>
 *         <li>代价：每个方法都需在 XML 中写 SQL 映射</li>
 *       </ul>
 *   </li>
 *   <li>本接口<b>不写</b> {@code @Mapper} 注解 —— 改用启动类 {@code @MapperScan("io.gitee.lujianshi.bidwinningbackend.mapper")} 统一扫描注册
 *       <ul>
 *         <li>Plus 官方推荐做法</li>
 *         <li>减少每个 Mapper 的样板代码</li>
 *       </ul>
 *   </li>
 *   <li>SQL 实现全部在 <b>resources/mapper/BidHandlerInfoMapper.xml</b> 中
 *       <ul>
 *         <li>namespace 必须等于本接口全限定名</li>
 *         <li>每个方法对应一个 SQL 标签（&lt;insert&gt; / &lt;update&gt; / &lt;delete&gt; / &lt;select&gt;）</li>
 *       </ul>
 *   </li>
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
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo
 * @see io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService
 */
@Mapper
public interface BidHandlerInfoMapper {

    /**
     * 插入一条经办人记录
     *
     * 对应 XML：{@code <insert id="insert" useGeneratedKeys="true" keyProperty="handlerId">}
     * 自增 ID 会在执行后<b>回填</b>到入参 entity 的 {@code handlerId} 字段。
     *
     * @param entity 待插入的实体（handlerId 字段可为空，由 DB 自增生成）
     * @return 影响行数（1=成功，0=失败）
     */
    int insert(BidHandlerInfo entity);

    /**
     * 按主键更新经办人
     *
     * 对应 XML：{@code <update id="updateById">}
     *
     * @param entity 待更新实体（必须携带 handlerId）
     * @return 影响行数（1=成功，0=记录不存在）
     */
    int updateById(BidHandlerInfo entity);

    /**
     * 按主键删除经办人
     *
     * 对应 XML：{@code <delete id="deleteById">}
     *
     * @param handlerId 主键 ID
     * @return 影响行数（1=成功，0=记录不存在）
     */
    int deleteById(Integer handlerId);

    /**
     * 按主键查询单条经办人
     *
     * 对应 XML：{@code <select id="selectById" resultMap="BaseResultMap">}
     * XML 中 {@code <resultMap>} 定义 Java 字段 ↔ DB 列映射（handlerId ↔ handler_id 等）。
     *
     * @param handlerId 主键 ID
     * @return 实体（未找到返回 null）
     */
    BidHandlerInfo selectById(Integer handlerId);

    /**
     * 查询全部经办人
     *
     * 对应 XML：{@code <select id="selectAll" resultMap="BaseResultMap">}
     * 注意：当前未分页，数据量大时建议后续加分页。
     *
     * @return 全部经办人列表（空表返回空 List 而非 null）
     */
    List<BidHandlerInfo> selectAll();
}
