package io.gitee.lujianshi.bidwinningbackend.service;

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
 * 定标通知 Service。继承 {@link BaseService}，重写 add 设 createdAt，新增批量新增 + 邮件通知。
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

    /** 重写 add：新增时设置 createdAt。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int add(BidWinningNotification entity) {
        entity.setCreatedAt(LocalDateTime.now());
        return super.add(entity);
    }

    /**
     * 批量新增定标通知。每条记录各自落库，全部成功或全部回滚。
     *
     * @param items 新增请求列表
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
     */
    public void sendOperationEmail(BidWinningNotification entity) {
        this.sendEmail(entity, "bid-winning-notification/create", "新增");
    }

    /**
     * 批量新增后异步发送汇总通知邮件。{@code outerTargetEmail} 为空则跳过。
     */
    public void sendBatchAddEmail(List<BidWinningNotification> entities, String outerTargetEmail) {
        this.sendBatchEmail(entities, outerTargetEmail, "bid-winning-notification/batch-add", "批量新增");
    }
}
