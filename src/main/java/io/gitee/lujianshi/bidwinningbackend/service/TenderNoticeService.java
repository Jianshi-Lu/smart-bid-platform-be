package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.tendernotice.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.TenderNotice;
import io.gitee.lujianshi.bidwinningbackend.entity.TenderNoticeStatusHistory;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.TenderNoticeMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.TenderNoticeStatusHistoryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 招标公告 Service。继承 {@link BaseService}，重写 add 设 createdAt/status，新增批量新增 + 状态变更。
 */
@Service
public class TenderNoticeService extends BaseService<TenderNotice> {

    private static final Set<String> VALID_TRANSITIONS = Set.of(
            "DRAFT->IN_PROGRESS", "IN_PROGRESS->COMPLETED"
    );

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
        entity.setStatus("DRAFT");
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
            entity.setStatus("DRAFT");
            mapper.insert(entity);
            created.add(entity);
        }
        return created;
    }

    @Transactional(rollbackFor = Exception.class)
    public TenderNotice transitionStatus(Integer noticeId, String toStatus, String remark) {
        TenderNotice entity = mapper.selectById(noticeId);
        if (entity == null) {
            throw new IllegalArgumentException("记录不存在: " + noticeId);
        }
        String fromStatus = entity.getStatus();
        String transition = fromStatus + "->" + toStatus;
        if (!VALID_TRANSITIONS.contains(transition)) {
            throw new IllegalArgumentException("非法状态变更: " + transition);
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
