package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import io.gitee.lujianshi.bidwinningbackend.entity.TendererDetail;
import io.gitee.lujianshi.bidwinningbackend.mapper.TendererDetailMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 投标人明细加载器。按 tendererId 查 tenderer_detail 表拼成 Markdown。
 */
@Service
public class TendererDetailLoader {

    @Resource
    private TendererDetailMapper tendererDetailMapper;

    /**
     * 加载指定投标人明细。
     *
     * @param tendererId 投标人 ID
     * @return 投标人明细文本（Markdown 格式），无数据时返回空字符串
     */
    public String loadDetail(Integer tendererId) {
        TendererDetail detail = tendererDetailMapper.selectById(tendererId);
        if (detail == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 投标人基本信息\n");
        sb.append("- 投标人名称：").append(detail.getTendererName() != null ? detail.getTendererName() : "未知").append("\n");
        sb.append("- 报价：").append(detail.getQuotePrice() != null ? detail.getQuotePrice() : "未知").append("元\n");
        sb.append("- 联系方式：").append(detail.getTendererContact() != null ? detail.getTendererContact() : "未知").append("\n");
        sb.append("- 资信材料：").append(detail.getCredentials() != null ? detail.getCredentials() : "未提供").append("\n");
        return sb.toString();
    }
}
