package io.gitee.lujianshi.bidwinningbackend.service.bidwinningsupplierdetail;

import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningSupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidWinningSupplierDetailMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 中标结果明细 Service 层 — 业务逻辑编排
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表（bid_winning_supplier_detail，V5 建表）
 *      ↓
 *   ② Entity 层（BidWinningSupplierDetail，含 @TableName / @TableId）
 *      ↓
 *   ③ Mapper 接口（BidWinningSupplierDetailMapper）
 *      ↓
 *   ④ Mapper XML（PATCH 动态 SQL + deleteByIds + selectByIds）
 *      ↓
 *   ⑤ Service 层（本类，注入 Mapper 接口，业务编排）
 *      ↓
 *   ⑥ DTO 层（CreateRequest / UpdateRequest / BatchDeleteRequest）
 *      ↓
 *   ⑦ Controller 层（注入本 Service，返回 Result 包装）
 * </pre>
 *
 * <h3>设计原则（与 BidHandlerInfoService / BidWinningNotificationService 保持一致）</h3>
 * <ul>
 *   <li>本类<b>不继承</b> {@code IService<T>} —— 保持手写风格，方法签名可控</li>
 *   <li>本类<b>不继承</b> {@code ServiceImpl<Mapper, T>} —— 不用 Plus 提供的 service 增强</li>
 *   <li>直接注入 {@link BidWinningSupplierDetailMapper}（接口），由 Spring 注入 XML 生成的代理实现</li>
 *   <li>当前为简单 CRUD 转发，未来业务逻辑（如"按通知分组聚合"、"按供应商汇总"）放这里</li>
 * </ul>
 *
 * <h3>事务策略</h3>
 * <ul>
 *   <li><b>写方法</b>（{@code create} / {@code update} / {@code delete} / {@code deleteByIds}）加
 *       {@code @Transactional(rollbackFor = Exception.class)} —— 与 {@code BidHandlerInfoService} 风格一致</li>
 *   <li><b>读方法</b>（{@code getById} / {@code listAll}）不加事务</li>
 *   <li>{@code rollbackFor = Exception.class}：捕获所有异常（含运行时异常 + 检查异常），触发回滚</li>
 * </ul>
 *
 * <h3>PATCH 配合说明</h3>
 * <ul>
 *   <li>{@link #update(BidWinningSupplierDetail)} 入参 entity 允许字段为 null</li>
 *   <li>XML 动态 SQL 在 null 字段上跳过 SET（保留 DB 原值）</li>
 *   <li>完整 PATCH 语义详见 {@link io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailUpdateRequest}</li>
 * </ul>
 *
 * <h3>调用链路（以 patchBidWinningSupplierDetail 为例）</h3>
 * <pre>
 *   Controller.patchBidWinningSupplierDetail(id, request)
 *     → BidWinningSupplierDetailService.getById(id)         ← diff.before 来源
 *     → 构造新 entity（id + 3 字段，null 字段不更新）
 *     → BidWinningSupplierDetailService.update(newEntity)
 *       → BidWinningSupplierDetailMapper.updateById(entity)
 *         → BidWinningSupplierDetailMapper.xml &lt;update&gt; 动态 SQL（&lt;set&gt; + &lt;if&gt;）
 *           → MySQL 真实 SQL 执行（PATCH：只 SET 非 null 字段）
 *     → Controller 包装 Diff&lt;before, after&gt;
 * </pre>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.controller.bidwinningsupplierdetail.BidWinningSupplierDetailController
 * @see io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailCreateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailUpdateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailBatchDeleteRequest
 */
@Service
public class BidWinningSupplierDetailService {

    @Resource
    private BidWinningSupplierDetailMapper mapper;

    /**
     * 创建中标结果明细
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.createBidWinningSupplierDetail(request)
     *     → Service.create(entity)
     *       → Mapper.insert(entity)
     *         → XML &lt;insert&gt; → MySQL INSERT → 自增回填 detailId
     * </pre>
     *
     * @param entity 待创建实体（detailId 字段可为空，DB 自增回填）
     * @return 影响行数（1=成功，0=失败）
     */
    @Transactional(rollbackFor = Exception.class)
    public int create(BidWinningSupplierDetail entity) {
        return mapper.insert(entity);
    }

    /**
     * 按 ID 部分更新中标结果明细（PATCH 语义，配合 XML 动态 SQL）
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.patchBidWinningSupplierDetail(id, request)
     *     → Service.update(newEntity)
     *       → Mapper.updateById(entity)
     *         → XML &lt;update&gt; 动态 SQL（&lt;set&gt; + &lt;if test="xxx != null"&gt;）
     *         → 仅 SET 非 null 字段，null 字段保留 DB 原值
     * </pre>
     *
     * @param entity 待更新实体（必须携带 detailId；其他 3 字段为 null=不修改）
     * @return 影响行数（1=成功，0=记录不存在）
     */
    @Transactional(rollbackFor = Exception.class)
    public int update(BidWinningSupplierDetail entity) {
        return mapper.updateById(entity);
    }

    /**
     * 按 ID 删除中标结果明细
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.deleteBidWinningSupplierDetail(id)
     *     → Service.delete(id)
     *       → Mapper.deleteById(id)
     *         → XML &lt;delete&gt; → MySQL DELETE
     * </pre>
     *
     * @param id 主键 ID
     * @return 影响行数（1=成功，0=记录不存在）
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        return mapper.deleteById(id);
    }

    /**
     * 按 ID 查询单条中标结果明细
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.getByIdBidWinningSupplierDetail(id)
     *     → Service.getById(id)
     *       → Mapper.selectById(id)
     *         → XML &lt;select&gt; → MySQL SELECT → resultMap 映射
     * </pre>
     *
     * @param id 主键 ID
     * @return 实体（未找到返回 null）
     */
    public BidWinningSupplierDetail getById(Integer id) {
        return mapper.selectById(id);
    }

    /**
     * 查询全部中标结果明细（无分页）
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.listBidWinningSupplierDetail(pageNum, pageSize, sort)
     *     → Service.listAll()
     *       → Mapper.selectAll()
     *         → XML &lt;select&gt; → MySQL SELECT（PageHelper 加 LIMIT + COUNT）
     *         → List&lt;BidWinningSupplierDetail&gt; → PageInfo 包装 → Result 序列化
     * </pre>
     *
     * <p><b>注意</b>：当前 selectAll 返回全表，由 PageHelper 在 SQL 层加 LIMIT 拦截。
     * 实际数据量大时建议未来直接在 XML 写分页 SQL（{@code LIMIT page, size}），
     * 或换用 MyBatis-Plus {@code Page<T>}。</p>
     *
     * @return 全部中标结果明细列表（空表返回空 List 而非 null）
     */
    public List<BidWinningSupplierDetail> listAll() {
        return mapper.selectAll();
    }

    /**
     * 批量删除中标结果明细（按主键 ID 列表）— p3 升级版：返回实际删除的实体列表
     *
     * <p>调用链：</p>
     * <pre>
     *   Controller.batchDeleteBidWinningSupplierDetail(request)
     *     → Service.deleteByIds(ids)
     *       → Mapper.selectByIds(ids)         ← 知道哪些 ID 真的存在
     *       → Mapper.deleteByIds(existingIds) ← 真删
     *     → 返回删掉的实体列表
     * </pre>
     *
     * <p><b>设计：p3 升级版（data 返实体列表）</b>：</p>
     * <ul>
     *   <li>永远 200，<b>不</b>区分"全部不存在"与"部分不存在"</li>
     *   <li>返回实际删除的实体列表（前端展示用）</li>
     *   <li>不存在的 ID：不在返回列表中，前端 diff 自己请求的 ids 即可</li>
     *   <li><b>多 1 次 SELECT</b>（PK 索引，毫秒级）</li>
     *   <li><b>不</b>做完整性校验 / <b>不</b>做级联 / DB 物理顺序（无 ORDER BY）</li>
     *   <li>race condition 自洽：data 返的行 = 实际删的行（数量一致）</li>
     * </ul>
     *
     * @param ids 待删除的主键 ID 列表
     * @return 实际删除的中标结果明细实体列表（不存在的 ID 不会出现在结果中；空列表时返空 List）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<BidWinningSupplierDetail> deleteByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<BidWinningSupplierDetail> existing = mapper.selectByIds(ids);
        if (!existing.isEmpty()) {
            List<Integer> existingIds = existing.stream()
                    .map(BidWinningSupplierDetail::getDetailId)
                    .toList();
            mapper.deleteByIds(existingIds);
        }
        return existing;
    }
}
