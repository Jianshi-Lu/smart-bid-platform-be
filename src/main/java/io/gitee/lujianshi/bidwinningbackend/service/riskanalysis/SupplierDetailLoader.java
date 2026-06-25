package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.mapper.SupplierDetailMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 供应商明细加载器。按 supplierId 查 supplier_detail 表拼成 Markdown。
 *
 * <p>V10 重构后 supplier_detail 与 bid_winning_notification 的关联已断开，
 * 当前仅加载供应商基本信息。</p>
 */
@Service
public class SupplierDetailLoader {

    @Resource
    private SupplierDetailMapper supplierDetailMapper;

    /**
     * 加载指定供应商明细。
     *
     * @param supplierId 供应商 ID
     * @return 供应商明细文本（Markdown 格式），无数据时返回空字符串
     */
    public String loadDetail(Integer supplierId) {
        SupplierDetail detail = supplierDetailMapper.selectById(supplierId);
        if (detail == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 供应商基本信息\n");
        sb.append("- 供应商名称：").append(detail.getSupplierName() != null ? detail.getSupplierName() : "未知").append("\n");
        sb.append("- 报价：").append(detail.getQuotePrice() != null ? detail.getQuotePrice() : "未知").append("元\n");
        sb.append("- 联系方式：").append(detail.getSupplierContact() != null ? detail.getSupplierContact() : "未知").append("\n");
        return sb.toString();
    }
}
