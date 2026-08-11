package io.gitee.lujianshi.bidwinningbackend.credentialfile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资信文件实体。对应表 {@code credential_file}（V21/V23）。
 *
 * <p>每个原始文件对应一条记录，并拥有独立的 {@code fileUuid}。资信文件在创建时已
 * 绑定 {@code bidRecordId}；转换前 {@code markdownFilePath} 保持为 {@code null}。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("credential_file")
public class CredentialFile {

    @TableId(type = IdType.AUTO)
    private Long fileId;

    private String fileUuid;

    private Integer bidRecordId;

    private Integer fileOrder;

    private String originalFilename;

    private String contentType;

    private Long fileSize;

    private String originalSha256;

    /** 转换后 Markdown 正文 SHA-256（V46 起，对齐 knowledge_file）。 */
    private String convertedSha256;

    private String storageType;

    private String originalFilePath;

    /** 原始文件对象存储状态：STORING/STORED/FAILED。 */
    private String originalStorageStatus;

    /** 转换后 Markdown 的逻辑路径/object key。 */
    private String markdownFilePath;

    private ConvertStatus convertStatus;

    /** 独立资信向量化状态：PENDING/VECTORIZING/VECTORIZED/FAILED。 */
    private String indexStatus;

    /** vector-service 在 credential_file_chunks 中实际写入的 chunk 数。 */
    private Integer chunkCount;

    private String errorMessage;

    /** 失败阶段：UPLOAD/CONVERT/UNKNOWN（V46 起，对齐 knowledge_file）。 */
    private String errorStage;

    private LocalDateTime createdAt;

    private LocalDateTime convertedAt;

    private LocalDateTime indexedAt;

    private LocalDateTime boundAt;

    /** 软删除时间；非空表示已删除，历史追溯记录保留（V45 起）。 */
    private LocalDateTime deletedAt;

    /** 删除状态：NULL/DELETING/FAILED/DELETED。 */
    private String deletionStatus;

    /** 删除失败阶段：DELETE_OBJECT_ORIGINAL/DELETE_OBJECT_CONVERTED/MARK_DELETED/HISTORY/SYNC_CREDENTIALS。 */
    private String deletionFailedStage;

    /** 删除失败原因（内部排查用）。 */
    private String deletionErrorMessage;

    /** 删除开始时间，用于进程崩溃后超时抢占。 */
    private LocalDateTime deletionStartedAt;
}
