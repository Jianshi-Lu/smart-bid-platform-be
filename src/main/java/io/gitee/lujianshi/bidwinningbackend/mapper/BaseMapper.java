package io.gitee.lujianshi.bidwinningbackend.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通用 CRUD Mapper 基接口。定义 7 个标准方法（insert / updateById / deleteById / selectById / selectAll / deleteByIds / selectByIds），
 * 由具体 Mapper 接口继承并绑定对应 XML。
 *
 * @param <T> 实体类型
 */
public interface BaseMapper<T> {
    int insert(T entity);

    int updateById(T entity);

    int deleteById(Integer id);

    T selectById(Integer id);

    List<T> selectAll();

    /**
     * 按主键 ID 列表批量删除。
     *
     * @param ids 主键 ID 列表（XML 中通过 {@code collection="ids"} 遍历）
     */
    int deleteByIds(@Param("ids") List<Integer> ids);

    /**
     * 按主键 ID 列表批量查询。
     *
     * @param ids 主键 ID 列表（XML 中通过 {@code collection="ids"} 遍历）
     */
    List<T> selectByIds(@Param("ids") List<Integer> ids);
}
