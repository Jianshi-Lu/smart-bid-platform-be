package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.tenderhandler.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.TenderHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.TenderHandlerInfoMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 招标经办人 Service。继承 {@link BaseService}，新增批量新增 + 邮件通知。
 */
@Service
public class TenderHandlerInfoService extends BaseService<TenderHandlerInfo> {

    @Resource
    private TenderHandlerInfoMapper mapper;

    @Override
    protected BaseMapper<TenderHandlerInfo> getMapper() {
        return mapper;
    }

    @Override
    protected Function<TenderHandlerInfo, Integer> getIdExtractor() {
        return TenderHandlerInfo::getTenderHandlerId;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<TenderHandlerInfo> batchAdd(List<AddRequest> items) {
        List<TenderHandlerInfo> created = new ArrayList<>();
        for (AddRequest item : items) {
            TenderHandlerInfo entity = new TenderHandlerInfo();
            entity.setHandlerName(item.getHandlerName());
            entity.setCompanyName(item.getCompanyName());
            entity.setDepartmentName(item.getDepartmentName());
            entity.setTargetEmail(item.getTargetEmail());
            mapper.insert(entity);
            created.add(entity);
        }
        return created;
    }

    public void sendOperationEmail(TenderHandlerInfo entity) {
        this.sendEmail(entity, "tender-handler-info/add", "新增");
    }

    public void sendBatchAddEmail(List<TenderHandlerInfo> entities, String outerTargetEmail) {
        this.sendBatchEmail(entities, outerTargetEmail, "tender-handler-info/batch-add", "批量新增");
    }
}
