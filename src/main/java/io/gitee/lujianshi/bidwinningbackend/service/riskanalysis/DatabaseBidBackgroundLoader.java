package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.mapper.SupplierDetailMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 基于数据库的供应商背景信息加载实现。
 *
 * <p>查询 {@code supplier_detail}，拼装为 Markdown 文本片段返回。</p>
 *
 * <h3>拼装格式（示例）</h3>
 * <pre>
 * ## 供应商基本信息
 * - 供应商名称：XXX公司
 * - 报价：1234567.89元
 * - 联系方式：13800138000
 * </pre>
 *
 * <p>注意：V10 重构后 supplier_detail 与 bid_winning_notification 的关联已断开，
 * 当前版本仅加载供应商基本信息。未来由后台模块重新建立关联。</p>
 *
 * @see BidBackgroundLoader
 */
@Service
public class DatabaseBidBackgroundLoader implements BidBackgroundLoader {

    @Resource
    private SupplierDetailMapper supplierDetailMapper;

    @Override
    public String loadBackground(Integer supplierId) {
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
