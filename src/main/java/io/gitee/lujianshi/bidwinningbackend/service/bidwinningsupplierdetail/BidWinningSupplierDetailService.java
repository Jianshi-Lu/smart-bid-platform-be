package io.gitee.lujianshi.bidwinningbackend.service.bidwinningsupplierdetail;

import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningSupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidWinningSupplierDetailMapper;
import io.gitee.lujianshi.bidwinningbackend.service.BaseService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * 中标结果明细 Service — 继承 {@link BaseService}，提供标准 CRUD。
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseService&lt;BidWinningSupplierDetail&gt;
 *     ↑ extends
 *   BidWinningSupplierDetailService（本类）
 * </pre>
 *
 * <h3>继承的方法</h3>
 * <ul>
 *   <li>{@code int create(BidWinningSupplierDetail entity)} — 创建中标明细</li>
 *   <li>{@code int update(BidWinningSupplierDetail entity)} — 按主键 PATCH 更新</li>
 *   <li>{@code int delete(Integer id)} — 按主键删除</li>
 *   <li>{@code BidWinningSupplierDetail getById(Integer id)} — 按主键查询</li>
 *   <li>{@code List&lt;BidWinningSupplierDetail&gt; listAll()} — 查询全部（配合 PageHelper 分页）</li>
 *   <li>{@code List&lt;BidWinningSupplierDetail&gt; deleteByIds(List&lt;Integer&gt; ids)} — 批量删除，返回实际删除的实体列表</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.controller.bidwinningsupplierdetail.BidWinningSupplierDetailController
 */
@Service
public class BidWinningSupplierDetailService extends BaseService<BidWinningSupplierDetail> {

    @Resource
    private BidWinningSupplierDetailMapper mapper;

    @Override
    protected BaseMapper<BidWinningSupplierDetail> getMapper() {
        return mapper;
    }

    @Override
    protected Function<BidWinningSupplierDetail, Integer> getIdExtractor() {
        return BidWinningSupplierDetail::getDetailId;
    }
}
