package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.bidhandler.AddRequest;
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
 * 招标经办人 Service。继承 {@link BaseService}，新增批量新增 + 邮件通知（V18 重命名自 TenderHandlerInfoService）。
 */
@Service
public class BidHandlerInfoService extends BaseService<BidHandlerInfo> {

    @Resource
    private BidHandlerInfoMapper mapper;

    @Override
    protected BaseMapper<BidHandlerInfo, Integer> getMapper() {
        return mapper;
    }

    @Override
    protected Function<BidHandlerInfo, Integer> getIdExtractor() {
        return BidHandlerInfo::getBidHandlerId;
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
