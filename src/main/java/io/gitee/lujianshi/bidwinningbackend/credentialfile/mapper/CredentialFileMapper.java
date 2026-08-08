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
 * <p>当前仅提供基础 CRUD 和按上传批次查询，上传、删除、转换、绑定编排留在后续 Service 实现。</p>
 */
@Mapper
public interface CredentialFileMapper extends BaseMapper<CredentialFile, Long> {

    /** 查询指定投标记录下的全部资信文件，按文件顺序返回。 */
    List<CredentialFile> selectByBidRecordId(@Param("bidRecordId") Integer bidRecordId);

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

    /** 更新单个文件的 Markdown 路径和转换状态。 */
    int markConverted(
            @Param("fileId") Long fileId,
            @Param("markdownFilePath") String markdownFilePath,
            @Param("convertedSha256") String convertedSha256,
            @Param("convertedAt") LocalDateTime convertedAt);

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

    /** 原始对象存储失败：与 Markdown 转换状态分开留痕。 */
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
}
