package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.invitationforbid.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBidStatusHistory;
import io.gitee.lujianshi.bidwinningbackend.enums.BidNoticeStatus;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.InvitationForBidMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.InvitationForBidStatusHistoryMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 招标公告 Service。继承 {@link BaseService}，重写 add 设 createdAt/status，新增批量新增 + 状态变更（V18 重命名自 TenderNoticeService）。
 *
 * <p>状态机跳转合法性由 {@link BidNoticeStatus#canTransitionTo} 判定。</p>
 */
@Service
public class InvitationForBidService extends BaseService<InvitationForBid> {

    @Resource
    private InvitationForBidMapper mapper;

    @Resource
    private InvitationForBidStatusHistoryMapper historyMapper;

    @Resource
    private OperationHistoryService operationHistoryService;

    @Override
    protected BaseMapper<InvitationForBid, Integer> getMapper() {
        return mapper;
    }

    @Override
    protected Function<InvitationForBid, Integer> getIdExtractor() {
        return InvitationForBid::getInvitationId;
    }

    /**
     * 创建招标公告。自动设创建时间和初始状态 DRAFT。
     *
     * @param entity 待创建的实体（不含 invitationId / createdAt / status）
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int add(InvitationForBid entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setStatus(BidNoticeStatus.DRAFT);
        return super.add(entity);
    }

    @Override
    protected void afterAdd(InvitationForBid entity) {
        operationHistoryService.recordInvitation(
                entity.getInvitationId(),
                OperationType.CREATE,
                null,
                entity,
                null);
    }

    @Override
    protected void afterUpdate(InvitationForBid before, InvitationForBid after) {
        operationHistoryService.recordInvitation(
                after.getInvitationId(),
                OperationType.UPDATE,
                before,
                after,
                null);
    }

    @Override
    protected void afterDelete(InvitationForBid deleted) {
        if (deleted == null) {
            return;
        }
        operationHistoryService.recordInvitation(
                deleted.getInvitationId(),
                OperationType.DELETE,
                deleted,
                null,
                null);
    }

    /**
     * 批量新增招标公告。单事务，每条自动设 status=DRAFT。
     *
     * @param items 批量新增请求列表（最多 100 条）
     * @return 创建的实体列表（含自增 invitationId）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<InvitationForBid> batchAdd(List<AddRequest> items) {
        List<InvitationForBid> created = new ArrayList<>();
        for (AddRequest item : items) {
            InvitationForBid entity = new InvitationForBid();
            entity.setBidHandlerId(item.getBidHandlerId());
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
            entity.setStatus(BidNoticeStatus.DRAFT);
            mapper.insert(entity);
            operationHistoryService.recordInvitation(
                    entity.getInvitationId(),
                    OperationType.CREATE,
                    null,
                    entity,
                    null);
            created.add(entity);
        }
        return created;
    }

    /**
     * 变更招标公告状态。合法性由 {@link BidNoticeStatus#canTransitionTo} 判定。
     *
     * @param invitationId 公告 ID
     * @param toStatus     目标状态
     * @param remark       变更备注
     * @return 变更后的实体
     * @throws AppException 记录不存在、状态跳转非法或并发状态更新失败
     */
    @Transactional(rollbackFor = Exception.class)
    public InvitationForBid transitionStatus(Integer invitationId, BidNoticeStatus toStatus, String remark) {
        InvitationForBid entity = mapper.selectById(invitationId);
        if (entity == null) {
            throw new AppException(
                    AppErrorCode.INVITATION_NOT_FOUND,
                    "invitation",
                    "STATUS_UPDATE",
                    "LOAD_INVITATION",
                    "招标公告不存在: " + invitationId);
        }
        BidNoticeStatus fromStatus = entity.getStatus();
        if (!fromStatus.canTransitionTo(toStatus)) {
            throw new AppException(
                    AppErrorCode.INVITATION_INVALID_STATUS_TRANSITION,
                    "invitation",
                    "STATUS_UPDATE",
                    "VALIDATE_TRANSITION",
                    "非法状态变更: " + fromStatus + " -> " + toStatus);
        }

        int rows = mapper.updateStatusIfCurrent(
                invitationId,
                fromStatus,
                toStatus);
        if (rows != 1) {
            throw new AppException(
                    AppErrorCode.INVITATION_STATUS_CONFLICT,
                    "invitation",
                    "STATUS_UPDATE",
                    "UPDATE_STATUS",
                    "招标公告状态已发生变化，请刷新后重试: invitationId="
                            + invitationId + ", expectedStatus=" + fromStatus);
        }

        InvitationForBidStatusHistory history = new InvitationForBidStatusHistory();
        history.setInvitationId(invitationId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setRemark(remark);
        historyMapper.insert(history);

        InvitationForBid after = new InvitationForBid();
        BeanUtils.copyProperties(entity, after);
        after.setStatus(toStatus);
        operationHistoryService.recordInvitation(
                invitationId,
                OperationType.UPDATE,
                entity,
                after,
                remark);

        entity.setStatus(toStatus);
        return entity;
    }
}
