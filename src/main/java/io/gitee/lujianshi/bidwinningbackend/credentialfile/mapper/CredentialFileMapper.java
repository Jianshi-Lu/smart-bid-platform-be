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
            @Param("convertedAt") LocalDateTime convertedAt);

    /** 更新单个文件的失败状态。 */
    int markFailed(
            @Param("fileId") Long fileId,
            @Param("errorMessage") String errorMessage);

}
