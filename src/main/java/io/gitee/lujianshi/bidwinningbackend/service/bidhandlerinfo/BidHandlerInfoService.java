package io.gitee.lujianshi.bidwinningbackend.service.bidhandlerinfo;

import io.gitee.lujianshi.bidwinningbackend.controller.bidhandlerinfo.BidHandlerInfoController;
import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper;
import io.gitee.lujianshi.bidwinningbackend.service.BaseService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * 经办人 Service — 继承 {@link BaseService}，提供标准 CRUD。
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseService&lt;BidHandlerInfo&gt;
 *     ↑ extends
 *   BidHandlerInfoService（本类）
 * </pre>
 *
 * <h3>继承的方法</h3>
 * <ul>
 *   <li>{@code int create(BidHandlerInfo entity)} — 创建经办人</li>
 *   <li>{@code int update(BidHandlerInfo entity)} — 按主键 PATCH 更新</li>
 *   <li>{@code int delete(Integer id)} — 按主键删除</li>
 *   <li>{@code BidHandlerInfo getById(Integer id)} — 按主键查询</li>
 *   <li>{@code List&lt;BidHandlerInfo&gt; listAll()} — 查询全部（配合 PageHelper 分页）</li>
 *   <li>{@code List&lt;BidHandlerInfo&gt; deleteByIds(List&lt;Integer&gt; ids)} — 批量删除，返回实际删除的实体列表</li>
 * </ul>
 *
 * @see BidHandlerInfoController
 */
@Service
public class BidHandlerInfoService extends BaseService<BidHandlerInfo> {

    @Resource
    private BidHandlerInfoMapper mapper;

    @Override
    protected BaseMapper<BidHandlerInfo> getMapper() {
        return mapper;
    }
    @Override
    protected Function<BidHandlerInfo, Integer> getIdExtractor() {
        return BidHandlerInfo::getHandlerId;
    }
}
