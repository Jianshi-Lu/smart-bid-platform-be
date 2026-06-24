package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.controller.SupplierDetailController;
import io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.SupplierDetailMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * 供应商明细 Service — 继承 {@link BaseService}，提供标准 CRUD。
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseService&lt;SupplierDetail&gt;
 *     ↑ extends
 *   SupplierDetailService（本类）
 * </pre>
 *
 * <h3>继承的方法</h3>
 * <ul>
 *   <li>{@code int add(SupplierDetail entity)} — 新增供应商明细</li>
 *   <li>{@code int update(SupplierDetail entity)} — 按主键 PATCH 更新</li>
 *   <li>{@code int delete(Integer id)} — 按主键删除</li>
 *   <li>{@code SupplierDetail getById(Integer id)} — 按主键查询</li>
 *   <li>{@code List&lt;SupplierDetail&gt; listAll()} — 查询全部（配合 PageHelper 分页）</li>
 *   <li>{@code List&lt;SupplierDetail&gt; deleteByIds(List&lt;Integer&gt; ids)} — 批量删除，返回实际删除的实体列表</li>
 * </ul>
 *
 * @see SupplierDetailController
 */
@Service
public class SupplierDetailService extends BaseService<SupplierDetail> {

    @Resource
    private SupplierDetailMapper mapper;

    @Override
    protected BaseMapper<SupplierDetail> getMapper() {
        return mapper;
    }

    @Override
    protected Function<SupplierDetail, Integer> getIdExtractor() {
        return SupplierDetail::getSupplierId;
    }
}
