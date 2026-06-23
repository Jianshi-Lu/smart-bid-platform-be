package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.controller.BidHandlerInfoController;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandlerinfo.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 经办人 Service — 继承 {@link BaseService}，提供标准 CRUD 及批量新增。
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
 *   <li>{@code int add(BidHandlerInfo entity)} — 新增经办人</li>
 *   <li>{@code int update(BidHandlerInfo entity)} — 按主键 PATCH 更新</li>
 *   <li>{@code int delete(Integer id)} — 按主键删除</li>
 *   <li>{@code BidHandlerInfo getById(Integer id)} — 按主键查询</li>
 *   <li>{@code List&lt;BidHandlerInfo&gt; listAll()} — 查询全部（配合 PageHelper 分页）</li>
 *   <li>{@code List&lt;BidHandlerInfo&gt; deleteByIds(List&lt;Integer&gt; ids)} — 批量删除</li>
 * </ul>
 *
 * <h3>本类新增方法</h3>
 * <ul>
 *   <li>{@link #batchAdd(List)} — 批量新增经办人，返回已落库的实体列表</li>
 *   <li>{@link #sendOperationEmail(BidHandlerInfo)} — 单条新增后异步发通知邮件</li>
 *   <li>{@link #sendBatchAddEmail(List, String)} — 批量新增后异步发汇总邮件</li>
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
     * 批量新增经办人。
     *
     * <p>每条记录各自落库（含各自的 targetEmail），
     * 由 {@code @Transactional} 保证全部成功或全部回滚。</p>
     *
     * @param items 新增请求列表（复用 {@link AddRequest}）
     * @return 已落库的实体列表（含自增 handlerId）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<BidHandlerInfo> batchAdd(List<AddRequest> items) {
        List<BidHandlerInfo> created = new ArrayList<>();
        for (AddRequest item : items) {
            BidHandlerInfo entity = new BidHandlerInfo();
            entity.setHandlerName(item.getHandlerName());
            entity.setCompanyName(item.getCompanyName());
            entity.setDepartmentName(item.getDepartmentName());
            entity.setTargetEmail(item.getTargetEmail());
            mapper.insert(entity);
            created.add(entity);
        }
        return created;
    }

    /**
     * 单条新增后异步发送通知邮件。
     *
     * @param entity 新增后的实体（targetEmail 从 entity 反射读取）
     */
    public void sendOperationEmail(BidHandlerInfo entity) {
        this.sendEmail(entity, "bid-handler-info/add", "新增");
    }

    /**
     * 批量新增后异步发送汇总通知邮件。
     * <p>仅当 {@code outerTargetEmail} 非空时发送，为空则跳过。</p>
     *
     * @param entities         新增后的实体列表
     * @param outerTargetEmail 外层指定的汇总收件人（可选）
     */
    public void sendBatchAddEmail(List<BidHandlerInfo> entities, String outerTargetEmail) {
        this.sendBatchEmail(entities, outerTargetEmail, "bid-handler-info/batch-add", "批量新增");
    }
}
