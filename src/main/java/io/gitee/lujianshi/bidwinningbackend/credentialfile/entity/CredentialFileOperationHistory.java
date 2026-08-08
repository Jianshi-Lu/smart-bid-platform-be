package io.gitee.lujianshi.bidwinningbackend.credentialfile.entity;

import io.gitee.lujianshi.bidwinningbackend.entity.common.OperationHistoryRecord;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资信文件操作历史 — 数据库实体。对应表 {@code credential_file_operation_history}（V40）。
 *
 * <p>记录资信文件的上传/转换/删除，快照为 credential_file 整行 JSON。</p>
 *
 * @since V40
 */
@Data
@TableName("credential_file_operation_history")
public class CredentialFileOperationHistory implements OperationHistoryRecord {

    @TableId(type = IdType.AUTO)
    private Long historyId;

    private Long credentialFileId;

    private String operation;

    private String operatorType;

    private Long operatorId;

    private String beforeSnapshot;

    private String afterSnapshot;

    private String remark;

    private LocalDateTime createdAt;
}
