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
 *   ② Entity 层（BidHandlerInfo.java，Java ↔ DB 映射，含 @TableName / @TableId）
 *      ↓
 *   ③ Mapper 接口层（本类，只定义方法签名，无实现）
 *      ↓
 *   ④ Mapper XML 层（BidHandlerInfoMapper.xml，namespace 绑定本接口，SQL 实现）
 *      ↓
 *   ⑤ Service 层（注入本接口进行业务调用，如 BidHandlerInfoService）
 *      ↓
 *   ⑥ DTO 层（BidHandlerInfoCreateRequest / UpdateRequest）
 *      ↓
 *   ⑦ Controller 层
 * </pre>
 *
 * <h3>设计原则（坚持 Java 与 SQL 完全分离）</h3>
 * <ul>
 *   <li>本接口<b>不继承</b> {@code BaseMapper<T>} —— 不使用 Plus 的自动 CRUD
 *       <ul>
 *         <li>好处：拓展性强，复杂 SQL 可在 XML 自由控制（如 PATCH 动态 SQL）</li>
 *         <li>代价：每个方法都需在 XML 中写 SQL 映射</li>
 *       </ul>
 *   </li>
 *   <li>本接口<b>写</b> {@code @Mapper} 注解（与启动类 {@code @MapperScan("io.gitee.lujianshi.bidwinningbackend.mapper")} 双重保险）
 *       <ul>
 *         <li>{@code @MapperScan} 统一扫描所有子包 Mapper</li>
 *         <li>{@code @Mapper} 注解冗余但保留以提高 IDE 识别度</li>
 *       </ul>
 *   </li>
 *   <li>SQL 实现全部在 <b>resources/mapper/BidHandlerInfoMapper.xml</b> 中
 *       <ul>
 *         <li>namespace 必须等于本接口全限定名（一个字符都不能错）</li>
 *         <li>每个方法对应一个 SQL 标签（&lt;insert&gt; / &lt;update&gt; / &lt;delete&gt; / &lt;select&gt;）</li>
 *         <li>updateById 用动态 SQL（&lt;set&gt; + &lt;if&gt;）实现 PATCH 语义</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>方法命名规范</h3>
 * <ul>
 *   <li>{@code insert(entity)} — 插入，影响行数 1=成功（自增 ID 回填到 entity）</li>
 *   <li>{@code updateById(entity)} — 按主键更新（PATCH 风格：null 字段不更新），影响行数 1=成功</li>
 *   <li>{@code deleteById(id)} — 按主键删除，影响行数 1=成功</li>
 *   <li>{@code selectById(id)} — 按主键查询单条</li>
 *   <li>{@code selectAll()} — 查询全部（不传条件，分页由 PageHelper 拦截）</li>
 * </ul>
 *
 * <h3>对应数据库表 {@code bid_handler_info}</h3>
 * <ul>
 *   <li>建表脚本：{@code src/main/resources/sql/ddl/V1~V3__*.sql}（V1 建表 → V2 加字段 → V3 重命名）</li>
 *   <li>主键：{@code handler_id}（自增）</li>
 *   <li>字段数：4 个（handler_id / handler_name / company_name / department_name）</li>
 *   <li>详见 {@code docs/database.md} §2.1</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo
 * @see io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService
 * @see io.gitee.lujianshi.bidwinningbackend.controller.BidHandlerInfoController
 */
@Mapper
public interface BidHandlerInfoMapper {

    /**
     * 插入一条经办人记录
     *
     * 对应 XML：{@code <insert id="insert" useGeneratedKeys="true" keyProperty="handlerId">}
     * 自增 ID 会在执行后<b>回填</b>到入参 entity 的 {@code handlerId} 字段。
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.createBidHandlerInfo(request)
     *     → Service.create(entity)
     *       → Mapper.insert(entity)
     *         → XML &lt;insert&gt; → MySQL INSERT → 自增回填 handlerId
     * </pre>
     *
     * @param entity 待插入的实体（handlerId 字段可为空，由 DB 自增生成）
     * @return 影响行数（1=成功，0=失败）
     */
    int insert(BidHandlerInfo entity);

    /**
     * 按主键部分更新经办人（PATCH 语义，配合 XML 动态 SQL）
     *
     * 对应 XML：{@code <update id="updateById">} 用 {@code <set> + <if test="xxx != null">} 模式：
     * <ul>
     *   <li>字段为 null → 不更新（保留 DB 原值）</li>
     *   <li>字段非 null → 更新</li>
     * </ul>
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.patchBidHandlerInfo(id, request)
     *     → Service.update(newEntity)
     *       → Mapper.updateById(entity)
     *         → XML &lt;update&gt; 动态 SQL → MySQL UPDATE（仅 SET 非 null 字段）
     * </pre>
     *
     * @param entity 待更新实体（必须携带 handlerId；其他 3 字段为 null=不修改）
     * @return 影响行数（1=成功，0=记录不存在）
     */
    int updateById(BidHandlerInfo entity);

    /**
     * 按主键删除经办人
     *
     * 对应 XML：{@code <delete id="deleteById">}
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.deleteBidHandlerInfo(id)
     *     → Service.delete(id)
     *       → Mapper.deleteById(id)
     *         → XML &lt;delete&gt; → MySQL DELETE
     * </pre>
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
     * <p>调用链：</p>
     * <pre>
     *   Controller.getByIdBidHandlerInfo(id)
     *     → Service.getById(id)
     *       → Mapper.selectById(id)
     *         → XML &lt;select&gt; → MySQL SELECT → resultMap 映射回 Entity
     * </pre>
     *
     * @param handlerId 主键 ID
     * @return 实体（未找到返回 null）
     */
    BidHandlerInfo selectById(Integer handlerId);

    /**
     * 查询全部经办人
     *
     * 对应 XML：{@code <select id="selectAll" resultMap="BaseResultMap">}
     * 注意：当前无 LIMIT 子句，由 PageHelper 在 SQL 层拦截追加 LIMIT（ThreadLocal 模式）。
     * 数据量大时建议未来直接在 XML 写分页 SQL（{@code LIMIT page, size}）。
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.listBidHandlerInfo(page, size, sort)
     *     → Service.listAll()
     *       → Mapper.selectAll()
     *         → XML &lt;select&gt; → MySQL SELECT（PageHelper 加 LIMIT + COUNT）
     *         → List&lt;BidHandlerInfo&gt; → PageInfo 包装 → Result 序列化
     * </pre>
     *
     * @return 全部经办人列表（空表返回空 List 而非 null）
     */
    List<BidHandlerInfo> selectAll();
}
