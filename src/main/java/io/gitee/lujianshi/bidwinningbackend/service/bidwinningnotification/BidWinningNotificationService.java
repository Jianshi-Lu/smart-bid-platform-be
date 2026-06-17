package io.gitee.lujianshi.bidwinningbackend.service.bidwinningnotification;

import io.gitee.lujianshi.bidwinningbackend.controller.bidwinningnotification.BidWinningNotificationController;
import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidWinningNotificationMapper;
import io.gitee.lujianshi.bidwinningbackend.service.BaseService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * 定标通知 Service — 继承 {@link BaseService}，提供标准 CRUD。
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseService&lt;BidWinningNotification&gt;
 *     ↑ extends
 *   BidWinningNotificationService（本类）
 * </pre>
 *
 * <h3>继承的方法</h3>
 * <ul>
 *   <li>{@code int create(BidWinningNotification entity)} — 创建定标通知（重写：设置 createdAt）</li>
 *   <li>{@code int update(BidWinningNotification entity)} — 按主键 PATCH 更新</li>
 *   <li>{@code int delete(Integer id)} — 按主键删除</li>
 *   <li>{@code BidWinningNotification getById(Integer id)} — 按主键查询</li>
 *   <li>{@code List&lt;BidWinningNotification&gt; listAll()} — 查询全部（配合 PageHelper 分页）</li>
 *   <li>{@code List&lt;BidWinningNotification&gt; deleteByIds(List&lt;Integer&gt; ids)} — 批量删除，返回实际删除的实体列表</li>
 * </ul>
 *
 * @see BidWinningNotificationController
 */
@Service
public class BidWinningNotificationService extends BaseService<BidWinningNotification> {

    @Resource
    private BidWinningNotificationMapper mapper;

    @Override
    protected BaseMapper<BidWinningNotification> getMapper() {
        return mapper;
    }

    @Override
    protected Function<BidWinningNotification, Integer> getIdExtractor() {
        return BidWinningNotification::getNotificationId;
    }

    // 重写：创建时设置 createdAt
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int create(BidWinningNotification entity) {
        entity.setCreatedAt(LocalDateTime.now());
        return super.create(entity);
    }
}
