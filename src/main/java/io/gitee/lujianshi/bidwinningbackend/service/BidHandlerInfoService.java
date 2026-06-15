package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 经办人 Service 层 — 业务逻辑编排
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表（bid_handler_info）
 *      ↓
 *   ② Entity（BidHandlerInfo，含 @TableName / @TableId / @NotBlank / @NotNull）
 *      ↓
 *   ③ Mapper 接口（BidHandlerInfoMapper）
 *      ↓
 *   ④ Mapper XML（含 PATCH 动态 SQL）
 *      ↓
 *   ⑤ Service 层（本类，注入 Mapper 接口，业务编排）
 *      ↓
 *   ⑥ DTO（BidHandlerInfoCreateRequest / UpdateRequest）
 *      ↓
 *   ⑦ Controller（注入本 Service，返回 Result 包装）
 * </pre>
 *
 * <h3>设计原则（与 Mapper 风格保持一致：手写、不继承 Plus）</h3>
 * <ul>
 *   <li>本类<b>不继承</b> {@code IService<T>} —— 保持手写风格，方法签名可控</li>
 *   <li>本类<b>不继承</b> {@code ServiceImpl<Mapper, T>} —— 不用 Plus 提供的 service 增强</li>
 *   <li>直接注入 {@link BidHandlerInfoMapper}（接口），由 Spring 注入 XML 生成的代理实现</li>
 *   <li>当前只有简单 CRUD 转发，未来业务逻辑（如"按公司分组"、"经办人离职归档"）放这里</li>
 * </ul>
 *
 * <h3>为什么用 {@code @Resource(jakarta)} 字段注入而非构造器注入？</h3>
 * <ul>
 *   <li>字段注入代码更简洁（节省模板）</li>
 *   <li>缺点：不便于单元测试、可能掩盖循环依赖</li>
 *   <li>当前项目风格以简洁为主；如要严格，可改 Lombok {@code @RequiredArgsConstructor} + final 字段</li>
 *   <li>从 {@code @Autowired(spring)} 迁移到 {@code @Resource(jakarta)}：Spring 6+ 推荐使用 jakarta 注解，
 *       {@code @Resource} 默认按名称注入（与 {@code @Autowired} 默认按类型不同），当前只有一个 Mapper 实现时行为一致</li>
 * </ul>
 *
 * <h3>调用链路（以 patchBidHandlerInfo 为例）</h3>
 * <pre>
 *   Controller.patchBidHandlerInfo(id, request)
 *     → BidHandlerInfoService.getById(id)         ← diff.before 来源
 *     → 构造新 entity（id + 3 字段，null 字段不更新）
 *     → BidHandlerInfoService.update(newEntity)
 *       → BidHandlerInfoMapper.updateById(entity)
 *         → BidHandlerInfoMapper.xml &lt;update&gt; 动态 SQL（&lt;set&gt; + &lt;if&gt;）
 *           → MySQL 真实 SQL 执行（PATCH：只 SET 非 null 字段）
 *     → Controller 包装 Diff&lt;before, after&gt;
 * </pre>
 *
 * <h3>PATCH 配合说明</h3>
 * <ul>
 *   <li>{@link #update(BidHandlerInfo)} 入参 entity 允许字段为 null</li>
 *   <li>XML 动态 SQL 在 null 字段上跳过 SET（保留 DB 原值）</li>
 *   <li>完整 PATCH 语义详见 {@link io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoUpdateRequest}</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper
 * @see io.gitee.lujianshi.bidwinningbackend.controller.BidHandlerInfoController
 * @see io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoCreateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoUpdateRequest
 */
@Service
public class BidHandlerInfoService {

    @Resource
    private BidHandlerInfoMapper mapper;

    /**
     * 创建经办人
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.create(request)
     *     → BidHandlerInfoService.create(entity)
     *       → BidHandlerInfoMapper.insert(entity)
     *         → BidHandlerInfoMapper.xml &lt;insert&gt; 标签（INSERT + 自增回填）
     * </pre>
     *
     * @param entity 待创建实体（handlerId 字段可为空，DB 自增回填）
     * @return 影响行数（1=成功，0=失败）
     */
    public int create(BidHandlerInfo entity) {
        return mapper.insert(entity);
    }

    /**
     * 按 ID 部分更新经办人（PATCH 语义，配合 XML 动态 SQL）
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.patchBidHandlerInfo(id, request)
     *     → BidHandlerInfoService.update(newEntity)
     *       → BidHandlerInfoMapper.updateById(entity)
     *         → BidHandlerInfoMapper.xml &lt;update&gt; 标签（&lt;set&gt; + &lt;if test="xxx != null"&gt;）
     *         → 仅 SET 非 null 字段，null 字段保留 DB 原值
     * </pre>
     *
     * @param entity 待更新实体（必须携带 handlerId；其他 3 字段为 null=不修改）
     * @return 影响行数（1=成功，0=记录不存在）
     */
    public int update(BidHandlerInfo entity) {
        return mapper.updateById(entity);
    }

    /**
     * 按 ID 删除经办人
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.delete(id)
     *     → BidHandlerInfoService.delete(id)
     *       → BidHandlerInfoMapper.deleteById(id)
     *         → BidHandlerInfoMapper.xml &lt;delete&gt; 标签
     * </pre>
     *
     * @param id 主键 ID
     * @return 影响行数（1=成功，0=记录不存在）
     */
    public int delete(Integer id) {
        return mapper.deleteById(id);
    }

    /**
     * 按 ID 查询单条经办人
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.getById(id)
     *     → BidHandlerInfoService.getById(id)
     *       → BidHandlerInfoMapper.selectById(id)
     *         → BidHandlerInfoMapper.xml &lt;select&gt; 标签（resultMap 映射）
     * </pre>
     *
     * @param id 主键 ID
     * @return 实体（未找到返回 null）
     */
    public BidHandlerInfo getById(Integer id) {
        return mapper.selectById(id);
    }

    /**
     * 查询全部经办人（无分页）
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.list(page, size, sort)
     *     → BidHandlerInfoService.listAll()
     *       → BidHandlerInfoMapper.selectAll()
     *         → BidHandlerInfoMapper.xml &lt;select&gt; 标签
     *         → PageHelper 自动追加 LIMIT（ThreadLocal 模式）
     * </pre>
     *
     * <p><b>注意</b>：当前 selectAll 返回全表，由 PageHelper 在 SQL 层加 LIMIT 拦截。
     * 实际数据量大时建议未来直接在 XML 写分页 SQL（{@code LIMIT page, size}），
     * 或换用 MyBatis-Plus {@code Page<T>}。</p>
     *
     * @return 全部经办人列表（空表返回空 List 而非 null）
     */
    public List<BidHandlerInfo> listAll() {
        return mapper.selectAll();
    }

    /**
     * 批量删除经办人（按主键 ID 列表）
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.batchDeleteBidHandlerInfo(request)
     *     → Service.deleteByIds(ids)
     *       → Mapper.deleteByIds(ids)
     *         → XML &lt;delete&gt; + &lt;foreach&gt; → MySQL DELETE
     * </pre>
     *
     * <p><b>设计：最小实现（p3 方案）</b></p>
     * <ul>
     *   <li><b>不</b>做事务封装：单条 SQL 自动事务，DB 层保证原子性</li>
     *   <li><b>不</b>做级联删除：本表无外键，删除不涉及子表</li>
     *   <li><b>不</b>做完整性校验：列表中不存在的 ID 不会报错，由 MySQL IN 自然忽略</li>
     *   <li>空列表 / null 在 Service 层拦截返 0，避免 Mapper XML 拼接出非法 SQL</li>
     * </ul>
     *
     * @param ids 待删除的主键 ID 列表
     * @return 实际删除的经办人实体列表（不存在的 ID 不会出现在结果中；空列表时返空 List）
     */
    public List<BidHandlerInfo> deleteByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // 1. SELECT 存在的行（PK 索引，毫秒级）
        List<BidHandlerInfo> existing = mapper.selectByIds(ids);
        // 2. 删掉它们
        if (!existing.isEmpty()) {
            List<Integer> existingIds = existing.stream()
                    .map(BidHandlerInfo::getHandlerId)
                    .toList();
            mapper.deleteByIds(existingIds);
        }
        // 3. 返回删掉的实体行（前端展示用）
        return existing;
    }
}
