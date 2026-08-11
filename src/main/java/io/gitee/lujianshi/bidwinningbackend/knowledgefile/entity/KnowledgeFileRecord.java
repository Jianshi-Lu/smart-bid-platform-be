package io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文件生命周期记录 — 数据库实体。对应表 {@code knowledge_file}（V32）。
 *
 * <p>每个知识文件对应一行，是知识入库链路的唯一事实源：
 * {@code documentUuid} 同时是对象存储目录根与 vector-service chunk 的稳定身份。
 * 转换（original → Markdown）与向量化（Markdown → Chroma）分为两个状态列，
 * 失败记录保留对象与错误信息，支持按失败点重试。</p>
 *
 * @since V32
 */
@Data
@TableName("knowledge_file")
public class KnowledgeFileRecord {

    @TableId(type = IdType.AUTO)
    private Long knowledgeFileId;

    /** 文件单元 UUID（对象目录根 + Chroma 身份）。 */
    private String documentUuid;

    private String originalFilename;

    private String contentType;

    private Long fileSize;

    /** 原始文件 SHA-256（对象存储写入前/回读后双重校验）。 */
    private String originalSha256;

    /** Markdown 正文 SHA-256。 */
    private String convertedSha256;

    /** 存储介质：LOCAL / MINIO。 */
    private String storageType;

    private String originalFilePath;

    /** 原始文件对象存储状态：STORING/STORED/FAILED。 */
    private String originalStorageStatus;

    /** 转换后 Markdown 的逻辑路径/object key。 */
    private String markdownFilePath;

    /** 转换状态：UPLOADED/CONVERTING/CONVERTED/FAILED。 */
    private String convertStatus;

    /** 向量化状态：PENDING/VECTORIZING/VECTORIZED/FAILED。 */
    private String indexStatus;

    /** vector-service 实际写入 Chroma 的 chunk 数。 */
    private Integer chunkCount;

    private String errorMessage;

    /** 失败阶段：CONVERT/VECTORIZE/UNKNOWN。 */
    private String errorStage;

    private LocalDateTime createdAt;

    private LocalDateTime convertedAt;

    private LocalDateTime indexedAt;

    /** 软删除时间；非空表示已删除，历史追溯记录保留。 */
    private LocalDateTime deletedAt;

    /** 删除状态：NULL/DELETING/FAILED/DELETED（V44 起）。 */
    private String deletionStatus;

    /** 删除失败阶段：DELETE_VECTORS/DELETE_OBJECT_ORIGINAL/DELETE_OBJECT_CONVERTED/MARK_DELETED/HISTORY。 */
    private String deletionFailedStage;

    /** 删除失败原因（内部排查用）。 */
    private String deletionErrorMessage;

    /** 删除开始时间，用于进程崩溃后超时抢占。 */
    private LocalDateTime deletionStartedAt;
}
