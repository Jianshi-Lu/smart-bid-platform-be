package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.tendernotice.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.TenderNotice;
import io.gitee.lujianshi.bidwinningbackend.entity.TenderNoticeStatusHistory;
import io.gitee.lujianshi.bidwinningbackend.enums.TenderNoticeStatus;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.TenderNoticeMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.TenderNoticeStatusHistoryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 招标公告 Service。继承 {@link BaseService}，重写 add 设 createdAt/status，新增批量新增 + 状态变更。
 *
 * <p>状态机跳转合法性由 {@link TenderNoticeStatus#canTransitionTo} 判定。</p>
 */
@Service
public class TenderNoticeService extends BaseService<TenderNotice> {

    @Resource
    private TenderNoticeMapper mapper;

    @Resource
    private TenderNoticeStatusHistoryMapper historyMapper;

    @Override
    protected BaseMapper<TenderNotice> getMapper() {
        return mapper;
    }

    @Override
    protected Function<TenderNotice, Integer> getIdExtractor() {
        return TenderNotice::getNoticeId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int add(TenderNotice entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setStatus(TenderNoticeStatus.DRAFT);
        return super.add(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<TenderNotice> batchAdd(List<AddRequest> items) {
        List<TenderNotice> created = new ArrayList<>();
        for (AddRequest item : items) {
            TenderNotice entity = new TenderNotice();
            entity.setTenderHandlerId(item.getTenderHandlerId());
            entity.setProjectNo(item.getProjectNo());
            entity.setPurchaseTaskNo(item.getPurchaseTaskNo());
            entity.setPlatformCategory(item.getPlatformCategory());
            entity.setTenderProcess(item.getTenderProcess());
            entity.setTenderMethod(item.getTenderMethod());
            entity.setPurchaseMethod(item.getPurchaseMethod());
            entity.setIsFixedPrice(item.getIsFixedPrice());
            entity.setHasDocumentFee(item.getHasDocumentFee());
            entity.setDeposit(item.getDeposit());
            entity.setCreditReportRequired(item.getCreditReportRequired());
            entity.setRegistrationDeadline(item.getRegistrationDeadline());
            entity.setTechnicalTenderDeadline(item.getTechnicalTenderDeadline());
            entity.setBusinessTenderDeadline(item.getBusinessTenderDeadline());
            entity.setNotes(item.getNotes());
            entity.setPurchaseCompany(item.getPurchaseCompany());
            entity.setPurchaseDepartment(item.getPurchaseDepartment());
            entity.setTenderingParty(item.getTenderingParty());
            entity.setStatus(TenderNoticeStatus.DRAFT);
            mapper.insert(entity);
            created.add(entity);
        }
        return created;
    }

    /**
     * 变更招标公告状态。合法性由 {@link TenderNoticeStatus#canTransitionTo} 判定。
     *
     * @param noticeId 公告 ID
     * @param toStatus 目标状态
     * @param remark 变更备注
     * @return 变更后的实体
     * @throws IllegalArgumentException 记录不存在或状态跳转非法
     */
    @Transactional(rollbackFor = Exception.class)
    public TenderNotice transitionStatus(Integer noticeId, TenderNoticeStatus toStatus, String remark) {
        TenderNotice entity = mapper.selectById(noticeId);
        if (entity == null) {
            throw new IllegalArgumentException("记录不存在: " + noticeId);
        }
        TenderNoticeStatus fromStatus = entity.getStatus();
        if (!fromStatus.canTransitionTo(toStatus)) {
            throw new IllegalArgumentException(
                    "非法状态变更: " + fromStatus + " -> " + toStatus);
        }
        entity.setStatus(toStatus);
        mapper.updateById(entity);

        TenderNoticeStatusHistory history = new TenderNoticeStatusHistory();
        history.setNoticeId(noticeId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setRemark(remark);
        historyMapper.insert(history);

        return entity;
    }
}
