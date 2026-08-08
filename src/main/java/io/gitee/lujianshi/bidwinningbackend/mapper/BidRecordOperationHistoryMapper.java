package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecordOperationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 投标记录操作历史 Mapper（V35）。继承 {@link OperationHistoryBaseMapper}，绑定
 * {@code BidRecordOperationHistoryMapper.xml}。
 *
 * @since V35
 */
@Mapper
public interface BidRecordOperationHistoryMapper
        extends OperationHistoryBaseMapper<BidRecordOperationHistory> {

    /** 按投标记录 ID 查询操作历史（时间倒序）。 */
    List<BidRecordOperationHistory> selectByBidRecordId(
            @Param("bidRecordId") Integer bidRecordId);
}
