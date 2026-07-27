package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import io.gitee.lujianshi.bidwinningbackend.entity.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 投标记录加载器。按 bidRecordId 查 bid_record 表拼成 Markdown（V18 重命名自 TendererDetailLoader）。
 */
@Service
public class BidRecordLoader {

    @Resource
    private BidRecordMapper bidRecordMapper;

    /**
     * 加载指定投标记录的基本信息。
     *
     * @param bidRecordId 投标记录 ID
     * @return Markdown 格式文本，无数据时返回空字符串
     */
    public String loadDetail(Integer bidRecordId) {
        BidRecord record = bidRecordMapper.selectById(bidRecordId);
        if (record == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 投标人基本信息\n");
        sb.append("- 投标人名称：").append(record.getBidderName() != null ? record.getBidderName() : "未知").append("\n");
        sb.append("- 报价：").append(record.getQuotePrice() != null ? record.getQuotePrice() : "未知").append("元\n");
        sb.append("- 联系方式：").append(record.getBidderContact() != null ? record.getBidderContact() : "未知").append("\n");
        sb.append("- 投标次序：第").append(record.getBidNo()).append("次\n");
        return sb.toString();
    }
}
