package io.gitee.lujianshi.bidwinningbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.entity.bidhandler.BidHandlerInfoOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecordOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBidOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.entity.common.OperationHistoryRecord;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoOperationHistoryMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordOperationHistoryMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.CredentialFileOperationHistoryMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.InvitationForBidOperationHistoryMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.KnowledgeFileOperationHistoryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务操作历史服务。
 *
 * <p>三张历史表（公告/经办人/投标记录）结构一致，本服务统一负责：
 * 从 {@link CurrentAccountContext} 读取操作人、用 Jackson 把整行实体序列化为
 * JSON 快照、写入对应历史表，并提供按业务主键倒序查询。</p>
 *
 * @since V33
 */
@Service
public class OperationHistoryService {

    private final JsonMapper objectMapper;
    private final InvitationForBidOperationHistoryMapper invitationHistoryMapper;
    private final BidHandlerInfoOperationHistoryMapper bidHandlerHistoryMapper;
    private final BidRecordOperationHistoryMapper bidRecordHistoryMapper;
    private final CredentialFileOperationHistoryMapper credentialFileHistoryMapper;
    private final KnowledgeFileOperationHistoryMapper knowledgeFileHistoryMapper;

    public OperationHistoryService(
            InvitationForBidOperationHistoryMapper invitationHistoryMapper,
            BidHandlerInfoOperationHistoryMapper bidHandlerHistoryMapper,
            BidRecordOperationHistoryMapper bidRecordHistoryMapper,
            CredentialFileOperationHistoryMapper credentialFileHistoryMapper,
            KnowledgeFileOperationHistoryMapper knowledgeFileHistoryMapper) {
        this.invitationHistoryMapper = invitationHistoryMapper;
        this.bidHandlerHistoryMapper = bidHandlerHistoryMapper;
        this.bidRecordHistoryMapper = bidRecordHistoryMapper;
        this.credentialFileHistoryMapper = credentialFileHistoryMapper;
        this.knowledgeFileHistoryMapper = knowledgeFileHistoryMapper;
        this.objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    /** 记录一次招标公告操作（增删改）。 */
    public void recordInvitation(
            Integer invitationId,
            OperationType operation,
            Object before,
            Object after,
            String remark) {
        InvitationForBidOperationHistory history = new InvitationForBidOperationHistory();
        history.setInvitationId(invitationId);
        history.setOperation(operation.name());
        fillCommon(history, before, after, remark);
        invitationHistoryMapper.insert(history);
    }

    /** 记录一次招标经办人操作（增删改）。 */
    public void recordBidHandler(
            Integer bidHandlerId,
            OperationType operation,
            Object before,
            Object after,
            String remark) {
        BidHandlerInfoOperationHistory history = new BidHandlerInfoOperationHistory();
        history.setBidHandlerId(bidHandlerId);
        history.setOperation(operation.name());
        fillCommon(history, before, after, remark);
        bidHandlerHistoryMapper.insert(history);
    }

    /** 记录一次投标记录操作（增删改）。 */
    public void recordBidRecord(
            Integer bidRecordId,
            OperationType operation,
            Object before,
            Object after,
            String remark) {
        BidRecordOperationHistory history = new BidRecordOperationHistory();
        history.setBidRecordId(bidRecordId);
        history.setOperation(operation.name());
        fillCommon(history, before, after, remark);
        bidRecordHistoryMapper.insert(history);
    }

    /** 记录一次资信文件操作（增/改/删）。 */
    public void recordCredentialFile(
            Long credentialFileId,
            OperationType operation,
            Object before,
            Object after,
            String remark) {
        CredentialFileOperationHistory history = new CredentialFileOperationHistory();
        history.setCredentialFileId(credentialFileId);
        history.setOperation(operation.name());
        fillCommon(history, before, after, remark);
        credentialFileHistoryMapper.insert(history);
    }

    /** 记录一次知识文件操作（增/改/删）。 */
    public void recordKnowledgeFile(
            String documentUuid,
            OperationType operation,
            Object before,
            Object after,
            String remark) {
        KnowledgeFileOperationHistory history = new KnowledgeFileOperationHistory();
        history.setDocumentUuid(documentUuid);
        history.setOperation(operation.name());
        fillCommon(history, before, after, remark);
        knowledgeFileHistoryMapper.insert(history);
    }

    public List<InvitationForBidOperationHistory> listInvitationHistory(
            Integer invitationId) {
        return invitationHistoryMapper.selectByInvitationId(invitationId);
    }

    public List<BidHandlerInfoOperationHistory> listBidHandlerHistory(
            Integer bidHandlerId) {
        return bidHandlerHistoryMapper.selectByBidHandlerId(bidHandlerId);
    }

    public List<BidRecordOperationHistory> listBidRecordHistory(
            Integer bidRecordId) {
        return bidRecordHistoryMapper.selectByBidRecordId(bidRecordId);
    }

    public List<CredentialFileOperationHistory> listCredentialFileHistory(
            Long credentialFileId) {
        return credentialFileHistoryMapper.selectByCredentialFileId(credentialFileId);
    }

    public List<KnowledgeFileOperationHistory> listKnowledgeFileHistory(
            String documentUuid) {
        return knowledgeFileHistoryMapper.selectByDocumentUuid(documentUuid);
    }

    private void fillCommon(
            OperationHistoryRecord history,
            Object before,
            Object after,
            String remark) {
        CurrentAccountContext.CurrentAccount account = CurrentAccountContext.get();
        history.setOperatorType(account == null ? null : account.operatorType());
        history.setOperatorId(account == null ? null : account.operatorId());
        history.setBeforeSnapshot(toJson(before));
        history.setAfterSnapshot(toJson(after));
        history.setRemark(remark);
        history.setCreatedAt(LocalDateTime.now());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("操作历史快照序列化失败", e);
        }
    }
}
