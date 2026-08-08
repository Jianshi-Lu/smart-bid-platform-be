package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.bidhandler.BidHandlerInfoOperationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 招标经办人操作历史 Mapper（V34）。继承 {@link OperationHistoryBaseMapper}，绑定
 * {@code BidHandlerInfoOperationHistoryMapper.xml}。
 *
 * @since V34
 */
@Mapper
public interface BidHandlerInfoOperationHistoryMapper
        extends OperationHistoryBaseMapper<BidHandlerInfoOperationHistory> {

    /** 按经办人 ID 查询操作历史（时间倒序）。 */
    List<BidHandlerInfoOperationHistory> selectByBidHandlerId(
            @Param("bidHandlerId") Integer bidHandlerId);
}
