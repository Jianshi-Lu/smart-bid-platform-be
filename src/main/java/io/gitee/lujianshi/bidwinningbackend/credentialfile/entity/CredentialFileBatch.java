package io.gitee.lujianshi.bidwinningbackend.credentialfile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.CredentialFileBatchStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资信文件上传批次实体，对应 {@code credential_file_batch}（V22）。
 *
 * <p>一个投标记录可以有多个批次；每个批次对应一套一次性提交、提交成功后不可追加的资信材料。</p>
 *
 * @since V22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("credential_file_batch")
public class CredentialFileBatch {

    @TableId(type = IdType.INPUT)
    private String uploadBatchId;

    private Integer bidRecordId;

    private Integer batchNo;

    private CredentialFileBatchStatus batchStatus;

    private Integer fileCount;

    private Integer convertedFileCount;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime uploadedAt;

    private LocalDateTime convertingAt;

    private LocalDateTime convertedAt;

    private LocalDateTime updatedAt;
}
