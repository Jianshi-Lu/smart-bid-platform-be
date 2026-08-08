package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识文件生命周期 Mapper。继承 {@link BaseMapper}，绑定 {@code KnowledgeFileMapper.xml}。
 *
 * <p>V32 新增：状态推进全部使用条件更新，返回行数作为是否拿到处理权/状态是否匹配的依据，
 * 与资信文件的状态抢占语义一致。</p>
 *
 * @since V32
 */
@Mapper
public interface KnowledgeFileMapper extends BaseMapper<KnowledgeFileRecord, Long> {

    /** 按 document_uuid 查询未删除记录。 */
    KnowledgeFileRecord selectByDocumentUuid(
            @Param("documentUuid") String documentUuid);

    /** 按 document_uuid 查询记录（含已删除），供删除流程重试/补历史使用。 */
    KnowledgeFileRecord selectByDocumentUuidIncludingDeleted(
            @Param("documentUuid") String documentUuid);

    /** 分页查询未删除记录，PageHelper 负责分页与排序。 */
    List<KnowledgeFileRecord> selectPage(
            @Param("keyword") String keyword,
            @Param("convertStatus") String convertStatus,
            @Param("indexStatus") String indexStatus);

    /** 原始对象落库后回填路径、SHA-256 与存储介质。 */
    int updateOriginalMetadata(
            @Param("documentUuid") String documentUuid,
            @Param("originalFilePath") String originalFilePath,
            @Param("originalSha256") String originalSha256,
            @Param("storageType") String storageType);

    /** 原始对象存储失败：与转换、向量化状态分开留痕。 */
    int markOriginalStorageFailed(
            @Param("documentUuid") String documentUuid,
            @Param("errorMessage") String errorMessage);

    /** 抢占转换：UPLOADED/FAILED → CONVERTING。 */
    int markConverting(@Param("documentUuid") String documentUuid);

    /** 转换完成：CONVERTING → CONVERTED，回填路径与时间。 */
    int markConverted(
            @Param("documentUuid") String documentUuid,
            @Param("markdownFilePath") String markdownFilePath,
            @Param("convertedSha256") String convertedSha256,
            @Param("convertedAt") LocalDateTime convertedAt);

    /** 抢占向量化：PENDING/FAILED/VECTORIZED → VECTORIZING（需已 CONVERTED）。 */
    int markVectorizing(@Param("documentUuid") String documentUuid);

    /** 向量化完成：VECTORIZING → VECTORIZED，回填 chunk 数与时间。 */
    int markVectorized(
            @Param("documentUuid") String documentUuid,
            @Param("chunkCount") int chunkCount,
            @Param("indexedAt") LocalDateTime indexedAt);

    /** 转换阶段失败：状态置 FAILED，记录失败原因（保留对象供重试）。 */
    int markConvertFailed(
            @Param("documentUuid") String documentUuid,
            @Param("errorMessage") String errorMessage);

    /** 向量化阶段失败：状态置 FAILED，记录失败原因（保留 converted 对象供重试）。 */
    int markVectorizeFailed(
            @Param("documentUuid") String documentUuid,
            @Param("errorMessage") String errorMessage);

    /** 软删除：写入 deleted_at，保留追溯记录。 */
    int markDeleted(
            @Param("documentUuid") String documentUuid,
            @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 原子抢占删除权：未删除/删除失败/删除中已超时的记录进入 DELETING。
     *
     * @return 1 表示当前请求拿到删除权，0 表示另一请求正在删除
     */
    int beginDeletion(
            @Param("documentUuid") String documentUuid,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("staleBefore") LocalDateTime staleBefore);

    /** 删除失败落库：DELETING → FAILED，保留失败点与原因。 */
    int markDeletionFailed(
            @Param("documentUuid") String documentUuid,
            @Param("stage") String stage,
            @Param("errorMessage") String errorMessage);

    /** 删除完成：置 DELETED 终态并清空失败信息。 */
    int markDeletionSucceeded(@Param("documentUuid") String documentUuid);
}
