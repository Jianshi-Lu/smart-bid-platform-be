package io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity;

import io.gitee.lujianshi.bidwinningbackend.entity.common.OperationHistoryRecord;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文件操作历史 — 数据库实体。对应表 {@code knowledge_file_operation_history}（V41）。
 *
 * <p>记录知识文件的上传/转换/向量化/重试/删除，快照为 knowledge_file 整行 JSON，
 * 以 document_uuid 作为业务主键与追溯身份。</p>
 *
 * @since V41
 */
@Data
@TableName("knowledge_file_operation_history")
public class KnowledgeFileOperationHistory implements OperationHistoryRecord {

    @TableId(type = IdType.AUTO)
    private Long historyId;

    private String documentUuid;

    private String operation;

    private String operatorType;

    private Long operatorId;

    private String beforeSnapshot;

    private String afterSnapshot;

    private String remark;

    private LocalDateTime createdAt;
}
