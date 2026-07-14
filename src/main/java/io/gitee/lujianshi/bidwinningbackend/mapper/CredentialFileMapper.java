package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.CredentialFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资信文件 Mapper。绑定 {@code CredentialFileMapper.xml}。
 *
 * <p>当前仅提供基础 CRUD 和按上传批次查询，上传、删除、转换、绑定编排留在后续 Service 实现。</p>
 */
@Mapper
public interface CredentialFileMapper extends BaseMapper<CredentialFile, Long> {

    /**
     * 查询指定上传批次下的全部文件。
     *
     * @param uploadBatchId 上传批次 UUID
     * @return 文件列表，按 file_order、file_id 升序
     */
    List<CredentialFile> selectByUploadBatchId(@Param("uploadBatchId") String uploadBatchId);
}
