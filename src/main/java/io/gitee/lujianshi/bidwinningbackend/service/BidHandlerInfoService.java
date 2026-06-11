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
 *   ① 数据库表
 *      ↓
 *   ② Entity（BidHandlerInfo）
 *      ↓
 *   ③ Mapper 接口（BidHandlerInfoMapper）
 *      ↓
 *   ④ Mapper XML（SQL 实现）
 *      ↓
 *   ⑤ Service 层（本类，注入 Mapper 接口，业务编排）
 *      ↓
 *   ⑥ DTO（BidHandlerInfoCreateRequest）
 *      ↓
 *   ⑦ Controller（注入本 Service）
 * </pre>
 *
 * <h3>设计原则（与 Mapper 风格保持一致：手写、不继承 Plus）</h3>
 * <ul>
 *   <li>本类<b>不继承</b> {@code IService<T>} —— 保持手写风格，方法签名可控</li>
 *   <li>本类<b>不继承</b> {@code ServiceImpl<Mapper, T>} —— 不用 Plus 提供的 service 增强</li>
 *   <li>直接注入 {@link BidHandlerInfoMapper}（接口），由 Spring 注入 XML 生成的代理实现</li>
 *   <li>当前只有简单 CRUD 转发，未来业务逻辑（如"按公司分组"）放这里</li>
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
 * <h3>调用链路（以 create 为例）</h3>
 * <pre>
 *   Controller.create(request)
 *     → BidHandlerInfoService.create(entity)
 *       → BidHandlerInfoMapper.insert(entity)
 *         → BidHandlerInfoMapper.xml &lt;insert&gt; 标签
 *           → MySQL 真实 SQL 执行
 * </pre>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper
 * @see io.gitee.lujianshi.bidwinningbackend.controller.BidHandlerInfoController
 */
@Service
public class BidHandlerInfoService {

    @Resource
    private BidHandlerInfoMapper mapper;

    /**
     * 创建经办人
     *
     * @param entity 待创建实体（handlerId 字段可为空，DB 自增回填）
     * @return 影响行数（1=成功）
     */
    public int create(BidHandlerInfo entity) {
        return mapper.insert(entity);
    }

    /**
     * 按 ID 更新经办人
     *
     * @param entity 待更新实体（必须携带 handlerId）
     * @return 影响行数（1=成功，0=记录不存在）
     */
    public int update(BidHandlerInfo entity) {
        return mapper.updateById(entity);
    }

    /**
     * 按 ID 删除经办人
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
     * @param id 主键 ID
     * @return 实体（未找到返回 null）
     */
    public BidHandlerInfo getById(Integer id) {
        return mapper.selectById(id);
    }

    /**
     * 查询全部经办人
     *
     * @return 全部经办人列表
     */
    public List<BidHandlerInfo> listAll() {
        return mapper.selectAll();
    }
}
