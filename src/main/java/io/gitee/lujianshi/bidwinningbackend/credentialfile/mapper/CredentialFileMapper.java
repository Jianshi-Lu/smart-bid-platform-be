package io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资信文件 Mapper。绑定 {@code CredentialFileMapper.xml}。
 *
 * <p>提供文件状态条件更新、原始对象元数据回填和删除状态机所需的持久化操作；
 * 上传、转换和删除的跨资源编排由 Service 层完成。</p>
 */
@Mapper
public interface CredentialFileMapper extends BaseMapper<CredentialFile, Long> {

    /** 查询指定投标记录下的全部资信文件，按文件顺序返回。 */
    List<CredentialFile> selectByBidRecordId(@Param("bidRecordId") Integer bidRecordId);

    /**
     * 查询仍引用指定 original 对象的其他 active 文件数。
     *
     * <p>该查询仅用于兼容历史同 key 数据的删除保护；新对象路径已由文件 UUID 保证唯一。</p>
     */
    int countActiveByOriginalFilePathExcluding(
            @Param("originalFilePath") String originalFilePath,
            @Param("fileId") Long fileId);

    /** 查询仍引用指定 converted 对象的其他 active 文件数。 */
    int countActiveByMarkdownFilePathExcluding(
            @Param("markdownFilePath") String markdownFilePath,
            @Param("fileId") Long fileId);

    /** 按文件 ID 查询记录（含已删除），供删除流程重试/补历史使用。 */
    CredentialFile selectByIdIncludingDeleted(@Param("fileId") Long fileId);

    /**
     * 按文件 ID 原子抢占转换权，供无差别转换流程使用。
     *
     * <p>只有已上传、转换失败或已转换的文件可以重新进入转换中；
     * 已处于转换中的文件不会被并发请求再次抢占。</p>
     */
    int markConverting(
            @Param("fileId") Long fileId);

    /** 转换写对象前先持久化确定性目标路径，进程中断后仍可定位并补偿。 */
    int reserveMarkdownPath(
            @Param("fileId") Long fileId,
            @Param("markdownFilePath") String markdownFilePath);

    /** 更新单个文件的 Markdown 路径和转换状态。 */
    int markConverted(
            @Param("fileId") Long fileId,
            @Param("markdownFilePath") String markdownFilePath,
            @Param("convertedSha256") String convertedSha256,
            @Param("convertedAt") LocalDateTime convertedAt);

    /** 抢占向量化：已转换且 PENDING/FAILED/VECTORIZED → VECTORIZING。 */
    int markVectorizing(@Param("fileId") Long fileId);

    /** 向量化完成：VECTORIZING → VECTORIZED，并记录实际 chunk 数。 */
    int markVectorized(
            @Param("fileId") Long fileId,
            @Param("chunkCount") int chunkCount,
            @Param("indexedAt") LocalDateTime indexedAt);

    /** 向量化失败：只更新 index_status，不覆盖已完成的转换事实。 */
    int markVectorizeFailed(
            @Param("fileId") Long fileId,
            @Param("errorMessage") String errorMessage);

    /** 将进程中断遗留的 STORING/CONVERTING/VECTORIZING 收敛为可重试 FAILED。 */
    int markInterruptedProcessingFailed(
            @Param("fileId") Long fileId,
            @Param("errorMessage") String errorMessage);

    /** DB 与 Chroma 数量不一致时强制回到可重试索引失败态。 */
    int markIndexInconsistent(
            @Param("fileId") Long fileId,
            @Param("errorMessage") String errorMessage);

    /** 更新单个文件的失败状态。 */
    int markFailed(
            @Param("fileId") Long fileId,
            @Param("errorMessage") String errorMessage,
            @Param("errorStage") String errorStage);

    /** 原始对象落库后回填路径、SHA-256 与存储介质（对齐 knowledge_file）。 */
    int updateOriginalMetadata(
            @Param("fileId") Long fileId,
            @Param("originalFilePath") String originalFilePath,
            @Param("originalSha256") String originalSha256,
            @Param("storageType") String storageType);

    int markOriginalStorageFailed(
            @Param("fileId") Long fileId,
            @Param("errorMessage") String errorMessage);

    /** 软删除：写入 deleted_at，保留追溯记录（V45 起）。 */
    int markDeleted(
            @Param("fileId") Long fileId,
            @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 原子抢占删除权：未删除/删除失败/删除中已超时的记录进入 DELETING。
     *
     * @return 1 表示当前请求拿到删除权，0 表示另一请求正在删除
     */
    int beginDeletion(
            @Param("fileId") Long fileId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("staleBefore") LocalDateTime staleBefore);

    /** 删除失败落库：DELETING → FAILED，保留失败点与原因。 */
    int markDeletionFailed(
            @Param("fileId") Long fileId,
            @Param("stage") String stage,
            @Param("errorMessage") String errorMessage);

    /** 删除完成：置 DELETED 终态并清空失败信息。 */
    int markDeletionSucceeded(@Param("fileId") Long fileId);

    /** JVM 重启后旧 DELETING 已无执行者，改为 FAILED 以便幂等续删。 */
    int resetInterruptedDeletion(@Param("fileId") Long fileId);
}
