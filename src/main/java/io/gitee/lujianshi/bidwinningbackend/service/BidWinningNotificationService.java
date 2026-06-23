package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.controller.BidWinningNotificationController;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidWinningNotificationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 定标通知 Service — 继承 {@link BaseService}，提供标准 CRUD。
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseService&lt;BidWinningNotification&gt;
 *     ↑ extends
 *   BidWinningNotificationService（本类）
 * </pre>
 *
 * <h3>继承的方法</h3>
 * <ul>
 *   <li>{@code int add(BidWinningNotification entity)} — 新增定标通知（重写：设置 createdAt）</li>
 *   <li>{@code int update(BidWinningNotification entity)} — 按主键 PATCH 更新</li>
 *   <li>{@code int delete(Integer id)} — 按主键删除</li>
 *   <li>{@code BidWinningNotification getById(Integer id)} — 按主键查询</li>
 *   <li>{@code List&lt;BidWinningNotification&gt; listAll()} — 查询全部（配合 PageHelper 分页）</li>
 *   <li>{@code List&lt;BidWinningNotification&gt; deleteByIds(List&lt;Integer&gt; ids)} — 批量删除，返回实际删除的实体列表</li>
 * </ul>
 *
 * @see BidWinningNotificationController
 */
@Service
public class BidWinningNotificationService extends BaseService<BidWinningNotification> {

    @Resource
    private BidWinningNotificationMapper mapper;

    @Override
    protected BaseMapper<BidWinningNotification> getMapper() {
        return mapper;
    }

    @Override
    protected Function<BidWinningNotification, Integer> getIdExtractor() {
        return BidWinningNotification::getNotificationId;
    }

    // 重写：新增时设置 createdAt
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int add(BidWinningNotification entity) {
        entity.setCreatedAt(LocalDateTime.now());
        return super.add(entity);
    }

    /**
     * 批量新增定标通知。
     *
     * <p>每条记录各自落库（含各自的 targetEmail），
     * 由 {@code @Transactional} 保证全部成功或全部回滚。</p>
     *
     * @param items 新增请求列表（复用 {@link AddRequest}）
     * @return 已落库的实体列表（含自增 notificationId）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<BidWinningNotification> batchAdd(List<AddRequest> items) {
        List<BidWinningNotification> created = new ArrayList<>();
        for (AddRequest item : items) {
            BidWinningNotification entity = new BidWinningNotification();
            entity.setHandlerId(item.getHandlerId());
            entity.setProjectNo(item.getProjectNo());
            entity.setPurchaseTaskNo(item.getPurchaseTaskNo());
            entity.setPlatformCategory(item.getPlatformCategory());
            entity.setBiddingProcess(item.getBiddingProcess());
            entity.setBiddingMethod(item.getBiddingMethod());
            entity.setPurchaseMethod(item.getPurchaseMethod());
            entity.setIsFixedPrice(item.getIsFixedPrice());
            entity.setTargetCost(item.getTargetCost());
            entity.setDocumentFee(item.getDocumentFee());
            entity.setDeposit(item.getDeposit());
            entity.setCreditReportRequired(item.getCreditReportRequired());
            entity.setRegistrationDeadline(item.getRegistrationDeadline());
            entity.setTechBidDeadline(item.getTechBidDeadline());
            entity.setBusinessBidDeadline(item.getBusinessBidDeadline());
            entity.setBiddingStatus(item.getBiddingStatus());
            entity.setNotes(item.getNotes());
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
    public void sendOperationEmail(BidWinningNotification entity) {
        this.sendEmail(entity, "bid-winning-notification/create", "新增");
    }

    /**
     * 批量新增后异步发送汇总通知邮件。
     * <p>仅当 {@code outerTargetEmail} 非空时发送，为空则跳过。</p>
     *
     * @param entities         新增后的实体列表
     * @param outerTargetEmail 外层指定的汇总收件人（可选）
     */
    public void sendBatchAddEmail(List<BidWinningNotification> entities, String outerTargetEmail) {
        this.sendBatchEmail(entities, outerTargetEmail, "bid-winning-notification/batch-add", "批量新增");
    }
}
