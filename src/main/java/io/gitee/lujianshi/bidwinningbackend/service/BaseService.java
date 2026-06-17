package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

/**
 * 通用 CRUD Service 基类。
 * <p>封装 7 个标准业务方法（create / update / delete / getById / listAll / deleteByIds），
 * 写方法统一加 {@code @Transactional(rollbackFor = Exception.class)}。
 * 子类只需实现 {@link #getMapper()} 和 {@link #getIdExtractor()} 两个抽象方法。</p>
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseService&lt;T&gt;                         ← 本类，泛型 T = Entity 类型
 *     ↑ extends
 *   BidHandlerInfoService                  ← 经办人业务
 *   BidWinningNotificationService          ← 定标通知业务（重写 create 设 createdAt）
 *   BidWinningSupplierDetailService        ← 中标明细业务
 * </pre>
 *
 * <h3>调用链路</h3>
 * <pre>
 *   Controller → Service（本类方法）→ Mapper 接口（BaseMapper&lt;T&gt;）→ Mapper XML → MySQL
 * </pre>
 *
 * @param <T> 实体类型
 */
public abstract class BaseService<T> {
    protected abstract BaseMapper<T> getMapper();

    protected abstract Function<T, Integer> getIdExtractor();

    @Transactional(rollbackFor = Exception.class)
    public int create(T entity) {
        return getMapper().insert(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public int update(T entity) {
        return getMapper().updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        return getMapper().deleteById(id);
    }

    public T getById(Integer id) {
        return getMapper().selectById(id);
    }

    public List<T> listAll() {
        return getMapper().selectAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<T> deleteByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<T> existing = getMapper().selectByIds(ids);
        if (!existing.isEmpty()) {
            List<Integer> existingIds = existing.stream()
                    .map(getIdExtractor())
                    .toList();
            getMapper().deleteByIds(existingIds);
        }
        return existing;
    }
}
