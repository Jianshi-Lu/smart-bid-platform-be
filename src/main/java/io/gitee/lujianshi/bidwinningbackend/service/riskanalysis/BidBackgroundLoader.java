package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

/**
 * 供应商背景信息加载器接口。当前默认实现从数据库查询，未来可扩展为接入天眼查/企查查等外部数据源。
 *
 * @see DatabaseBidBackgroundLoader
 */
public interface BidBackgroundLoader {

    /**
     * 加载指定供应商的背景信息。
     *
     * @param supplierId 供应商 ID
     * @return 背景信息（Markdown 格式），无数据时返回空字符串
     */
    String loadBackground(Integer supplierId);
}
