package io.gitee.lujianshi.bidwinningbackend.service.bidhandler;

import io.gitee.lujianshi.bidwinningbackend.dto.bidhandler.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.bidhandler.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.InvitationForBidMapper;
import io.gitee.lujianshi.bidwinningbackend.service.audit.OperationHistoryService;
import io.gitee.lujianshi.bidwinningbackend.service.support.BaseService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 招标经办人 Service。继承 {@link BaseService}，新增批量新增 + 邮件通知（V18 重命名自 TenderHandlerInfoService）。
 */
@Service
public class BidHandlerInfoService extends BaseService<BidHandlerInfo> {

    @Resource
    private BidHandlerInfoMapper mapper;

    @Resource
    private OperationHistoryService operationHistoryService;

    @Resource
    private InvitationForBidMapper invitationForBidMapper;

    @Override
    protected BaseMapper<BidHandlerInfo, Integer> getMapper() {
        return mapper;
    }

    @Override
    protected Function<BidHandlerInfo, Integer> getIdExtractor() {
        return BidHandlerInfo::getBidHandlerId;
    }

    @Override
    protected void afterAdd(BidHandlerInfo entity) {
        operationHistoryService.recordBidHandler(
                entity.getBidHandlerId(),
                OperationType.CREATE,
                null,
                entity,
                null);
    }

    @Override
    protected void afterUpdate(BidHandlerInfo before, BidHandlerInfo after) {
        operationHistoryService.recordBidHandler(
                after.getBidHandlerId(),
                OperationType.UPDATE,
                before,
                after,
                null);
    }

    @Override
    protected void afterDelete(BidHandlerInfo deleted) {
        if (deleted == null) {
            return;
        }
        operationHistoryService.recordBidHandler(
                deleted.getBidHandlerId(),
                OperationType.DELETE,
                deleted,
                null,
                null);
    }

    /**
     * 公告对经办人使用逻辑外键。仍有公告引用时拒绝删除，避免公告详情、编辑下拉和审计快照
     * 指向不存在的经办人；成功删除和 DELETE 历史记录仍由 BaseService 同一事务完成。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        requireNoInvitations(List.of(id));
        return super.delete(id);
    }

    /** 批量删除同样先整体校验，防止批量端点绕过单条删除的引用约束。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<BidHandlerInfo> deleteByIds(List<Integer> ids) {
        requireNoInvitations(ids);
        return super.deleteByIds(ids);
    }

    private void requireNoInvitations(List<Integer> bidHandlerIds) {
        if (bidHandlerIds == null || bidHandlerIds.isEmpty()) {
            return;
        }
        for (Integer bidHandlerId : bidHandlerIds) {
            if (bidHandlerId == null) {
                continue;
            }
            if (invitationForBidMapper.countByBidHandlerId(bidHandlerId) > 0) {
                throw new AppException(
                        AppErrorCode.BID_HANDLER_HAS_INVITATIONS,
                        "bid-handler",
                        "DELETE",
                        "CHECK_INVITATIONS",
                        "招标经办人仍有关联招标公告，请先删除或改派公告: "
                                + bidHandlerId);
            }
        }
    }

    /**
     * 批量新增经办人。单事务。
     *
     * @param items 批量新增请求列表
     * @return 创建的实体列表（含自增 bidHandlerId）
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
            operationHistoryService.recordBidHandler(
                    entity.getBidHandlerId(),
                    OperationType.CREATE,
                    null,
                    entity,
                    null);
            created.add(entity);
        }
        return created;
    }

    /**
     * 发送单条创建通知邮件。
     *
     * @param entity 已创建的经办人实体
     */
    public void sendOperationEmail(BidHandlerInfo entity) {
        this.sendEmail(entity, "bid-handler-info/add", "新增");
    }

    /**
     * 发送批量创建通知邮件。
     *
     * @param entities         已创建的经办人实体列表
     * @param outerTargetEmail 收件人
     */
    public void sendBatchAddEmail(List<BidHandlerInfo> entities, String outerTargetEmail) {
        this.sendBatchEmail(entities, outerTargetEmail, "bid-handler-info/batch-add", "批量新增");
    }
}
