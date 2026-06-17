package io.gitee.lujianshi.bidwinningbackend.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通用 CRUD Mapper 基接口。
 * <p>定义 7 个标准数据访问方法（insert / updateById / deleteById / selectById / selectAll / deleteByIds / selectByIds），
 * 由具体 Mapper 接口继承并绑定对应 XML 实现。</p>
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseMapper&lt;T&gt;          ← 本接口，泛型 T = Entity 类型
 *     ↑ extends
 *   BidHandlerInfoMapper   ← 绑定 BidHandlerInfoMapper.xml
 *   BidWinningNotificationMapper  ← 绑定 BidWinningNotificationMapper.xml
 *   BidWinningSupplierDetailMapper ← 绑定 BidWinningSupplierDetailMapper.xml
 * </pre>
 *
 * <h3>调用链路</h3>
 * <pre>
 *   Controller → Service → Mapper 接口（本接口方法）→ Mapper XML（namespace.id）→ MySQL
 * </pre>
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
     * @param ids 主键 ID 列表，XML 中通过 {@code collection="ids"} 遍历
     */
    int deleteByIds(@Param("ids") List<Integer> ids);

    /**
     * @param ids 主键 ID 列表，XML 中通过 {@code collection="ids"} 遍历
     */
    List<T> selectByIds(@Param("ids") List<Integer> ids);
}
