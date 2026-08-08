package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileOperationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资信文件操作历史 Mapper（V40）。继承 {@link OperationHistoryBaseMapper}，绑定
 * {@code CredentialFileOperationHistoryMapper.xml}。
 *
 * @since V40
 */
@Mapper
public interface CredentialFileOperationHistoryMapper
        extends OperationHistoryBaseMapper<CredentialFileOperationHistory> {

    /** 按资信文件 ID 查询操作历史（时间倒序）。 */
    List<CredentialFileOperationHistory> selectByCredentialFileId(
            @Param("credentialFileId") Long credentialFileId);
}
