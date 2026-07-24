package io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资信文件上传批次 Mapper。
 *
 * <p>批次表不使用数据库外键，投标记录归属由 Service 在业务层校验。</p>
 *
 * @since V22
 */
@Mapper
public interface CredentialFileBatchMapper extends BaseMapper<CredentialFileBatch, String> {

    /**
     * 查询指定投标记录下的最大资信材料批次序号。
     *
     * @param bidRecordId 投标记录 ID
     * @return 当前最大批次序号；无历史批次时返回 null
     */
    Integer selectMaxBatchNo(@Param("bidRecordId") Integer bidRecordId);

    /**
     * 将批次标记为原始文件已全部上传并校验成功。
     *
     * @param uploadBatchId 上传批次 UUID
     * @param status        目标批次状态
     * @param uploadedAt    原始文件完成时间
     * @return 更新行数
     */
    int markUploaded(
            @Param("uploadBatchId") String uploadBatchId,
            @Param("status") String status,
            @Param("uploadedAt") LocalDateTime uploadedAt);

    /** 查询指定投标记录下的全部资信文件批次。 */
    List<CredentialFileBatch> selectByBidRecordId(
            @Param("bidRecordId") Integer bidRecordId);

    /** 按批次 ID 将批次置为转换中，供无差别转换流程使用。 */
    int claimConverting(
            @Param("uploadBatchId") String uploadBatchId,
            @Param("convertingAt") LocalDateTime convertingAt);

    /** 将批次标记为全部转换完成。 */
    int markConverted(
            @Param("uploadBatchId") String uploadBatchId,
            @Param("convertedFileCount") int convertedFileCount,
            @Param("convertedAt") LocalDateTime convertedAt);

    /** 将批次标记为转换失败。 */
    int markFailed(
            @Param("uploadBatchId") String uploadBatchId,
            @Param("convertedFileCount") int convertedFileCount,
            @Param("errorMessage") String errorMessage);
}
