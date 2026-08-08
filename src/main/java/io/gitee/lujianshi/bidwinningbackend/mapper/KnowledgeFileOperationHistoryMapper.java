package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileOperationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识文件操作历史 Mapper（V41）。继承 {@link OperationHistoryBaseMapper}，绑定
 * {@code KnowledgeFileOperationHistoryMapper.xml}。
 *
 * @since V41
 */
@Mapper
public interface KnowledgeFileOperationHistoryMapper
        extends OperationHistoryBaseMapper<KnowledgeFileOperationHistory> {

    /** 按 document_uuid 查询操作历史（时间倒序）。 */
    List<KnowledgeFileOperationHistory> selectByDocumentUuid(
            @Param("documentUuid") String documentUuid);
}
