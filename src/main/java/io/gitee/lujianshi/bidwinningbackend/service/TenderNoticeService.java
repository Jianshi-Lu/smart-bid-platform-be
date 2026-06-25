package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.tender.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.TenderNotice;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.TenderNoticeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 招标公告 Service。继承 {@link BaseService}，重写 add 设 createdAt，新增批量新增 + 邮件通知。
 */
@Service
public class TenderNoticeService extends BaseService<TenderNotice> {

    @Resource
    private TenderNoticeMapper mapper;

    @Override
    protected BaseMapper<TenderNotice> getMapper() {
        return mapper;
    }

    @Override
    protected Function<TenderNotice, Integer> getIdExtractor() {
        return TenderNotice::getNoticeId;
    }

    /** 重写 add：新增时设置 createdAt。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int add(TenderNotice entity) {
        entity.setCreatedAt(LocalDateTime.now());
        return super.add(entity);
    }

    /**
     * 批量新增招标公告。每条记录各自落库，全部成功或全部回滚。
     *
     * @param items 新增请求列表
     * @return 已落库的实体列表（含自增 noticeId）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<TenderNotice> batchAdd(List<AddRequest> items) {
        List<TenderNotice> created = new ArrayList<>();
        for (AddRequest item : items) {
            TenderNotice entity = new TenderNotice();
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
    public void sendOperationEmail(TenderNotice entity) {
        this.sendEmail(entity, "tender-notice/create", "新增");
    }

    /**
     * 批量新增后异步发送汇总通知邮件。{@code outerTargetEmail} 为空则跳过。
     */
    public void sendBatchAddEmail(List<TenderNotice> entities, String outerTargetEmail) {
        this.sendBatchEmail(entities, outerTargetEmail, "tender-notice/batch-add", "批量新增");
    }
}
