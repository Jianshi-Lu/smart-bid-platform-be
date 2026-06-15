package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /**
     * 按主键列表批量删除经办人
     *
     * 对应 XML：{@code <delete id="deleteByIds" parameterType="java.util.List">}
     * 用 {@code <foreach>} 拼接 {@code WHERE handler_id IN (...)} 子句。
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.batchDeleteBidHandlerInfo(request)
     *     → Service.deleteByIds(ids)
     *       → Mapper.deleteByIds(ids)
     *         → XML &lt;delete&gt; + &lt;foreach&gt; → MySQL DELETE（单条 IN SQL）
     * </pre>
     *
     * <p><b>注意</b>：本方法为<b>最小实现</b>，不预判一致性 / 不级联 / 不做事务。
     * 如果待删除的 ID 关联了其他表（如未来加的外键），DB 层不会拦截，由应用层负责。</p>
     *
     * @param ids 待删除的主键 ID 列表（不允许 null/empty，由 Service 层拦截）
     * @return 影响行数（实际删除条数；列表中不存在的 ID 不会被计数）
     */
    int deleteByIds(@Param("ids") List<Integer> ids);

    /**
     * 按主键列表查询经办人（用于批量删除前知道哪些 ID 真的存在）
     *
     * 对应 XML：{@code <select id="selectByIds" parameterType="java.util.List">}
     * 用 {@code <foreach>} 拼接 {@code WHERE handler_id IN (...)} 子句。
     *
     * <p>调用链：</p>
     * <pre>
     *   Service.deleteByIds(ids)
     *     → Mapper.selectByIds(ids)         ← 知道哪些 ID 真的存在
     *       → XML &lt;select&gt; + &lt;foreach&gt; → MySQL SELECT（PK 索引，毫秒级）
     *     → Mapper.deleteByIds(existingIds)
     *     → 返回删掉的实体列表
     * </pre>
     *
     * <p><b>用途</b>：批量删除端点的"返回实际删除的实体列表"——前端展示用。
     * 列表中不存在的 ID 不会出现在结果中（IN 自然不匹配）。</p>
     *
     * <p><b>排序</b>：DB 物理顺序（无 {@code ORDER BY}），最小实现。</p>
     *
     * @param ids 待查询的主键 ID 列表（不允许 null/empty，由 Service 层拦截）
     * @return 存在的经办人列表（不存在的 ID 不会返回；空列表时返空 List）
     */
    List<BidHandlerInfo> selectByIds(@Param("ids") List<Integer> ids);
}
