package io.gitee.lujianshi.bidwinningbackend.mapper;

/**
 * 操作历史 Mapper 基接口。
 *
 * <p>历史表是追加式审计数据，不继承通用 CRUD 契约（update/delete 会破坏审计完整性），
 * 只声明写入能力；按业务主键查询由具体 Mapper 各自声明。</p>
 *
 * @param <T> 操作历史实体类型
 */
public interface OperationHistoryBaseMapper<T> {

    /** 插入一条操作历史记录。 */
    int insert(T entity);
}
