package io.gitee.lujianshi.bidwinningbackend.service;

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
 * 经办人 Service。继承 {@link BaseService}，新增批量新增 + 邮件通知。
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
     * 批量新增经办人。每条记录各自落库（含各自的 targetEmail），全部成功或全部回滚。
     *
     * @param items 新增请求列表
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
     * 批量新增后异步发送汇总通知邮件。{@code outerTargetEmail} 为空则跳过。
     */
    public void sendBatchAddEmail(List<BidHandlerInfo> entities, String outerTargetEmail) {
        this.sendBatchEmail(entities, outerTargetEmail, "bid-handler-info/batch-add", "批量新增");
    }
}
