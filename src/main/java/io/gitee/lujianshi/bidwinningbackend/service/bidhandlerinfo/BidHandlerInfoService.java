package io.gitee.lujianshi.bidwinningbackend.service.bidhandlerinfo;

import io.gitee.lujianshi.bidwinningbackend.controller.BidHandlerInfoController;
import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper;
import io.gitee.lujianshi.bidwinningbackend.service.BaseService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * 经办人 Service — 继承 {@link BaseService}，提供标准 CRUD。
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseService&lt;BidHandlerInfo&gt;
 *     ↑ extends
 *   BidHandlerInfoService（本类）
 * </pre>
 *
 * <h3>继承的方法</h3>
 * <ul>
 *   <li>{@code int create(BidHandlerInfo entity)} — 创建经办人</li>
 *   <li>{@code int update(BidHandlerInfo entity)} — 按主键 PATCH 更新</li>
 *   <li>{@code int delete(Integer id)} — 按主键删除</li>
 *   <li>{@code BidHandlerInfo getById(Integer id)} — 按主键查询</li>
 *   <li>{@code List&lt;BidHandlerInfo&gt; listAll()} — 查询全部（配合 PageHelper 分页）</li>
 *   <li>{@code List&lt;BidHandlerInfo&gt; deleteByIds(List<Integer> ids)} — 批量删除，返回实际删除的实体列表</li>
 * </ul>
 *
 * <h3>本类新增方法（邮件通知，DTO 传参模式）</h3>
 * <ul>
 *   <li>{@link # sendOperationEmail(BidHandlerInfo, String)} — 创建经办人后由 Controller 显式调用，
 *       渲染 {@code crud-email-template} 模板异步发送 HTML 通知邮件。收件人由
 *       {@code AddRequest.targetEmail}（JSON body 字段，<b>不落库</b>）传入，
 *       null/空字符串 = 跳过发邮件。端点描述硬编码为 {@code "bid-handler-info/create"}，操作类型为 {@code "新增"}。</li>
 * </ul>
 *
 * @see BidHandlerInfoController
 */
@Service
public class BidHandlerInfoService extends BaseService<BidHandlerInfo> {

    @Resource
    private BidHandlerInfoMapper mapper;

    @Override
    protected BaseMapper<BidHandlerInfo> getMapper() {
        return mapper;
    }

    @Override
    protected Function<BidHandlerInfo, Integer> getIdExtractor() {
        return BidHandlerInfo::getHandlerId;
    }

    /**
     * 创建经办人后异步发送 HTML 通知邮件。
     *
     * <p>端点描述和操作类型当前硬编码为 {@code "bid-handler-info/create"} / {@code "新增"}。
     * 后续若其他业务方法（如 update / delete）也接入邮件通知，需重构为
     * {@link BaseService#sendEmail} 直接调用并显式传入 endpoint 和 operation 参数。</p>
     *
     * @param entity 创建后的实体（邮件 {@code data} 字段来源，targetEmail 从 entity.targetEmail 反射读取）
     */
    public void sendOperationEmail(BidHandlerInfo entity) {
        this.sendEmail(entity, "bid-handler-info/create", "新增");
    }
}
