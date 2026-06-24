package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

/**
 * 供应商背景信息加载器接口。
 *
 * <p>负责根据供应商 ID（supplierId）加载该供应商的完整背景信息，
 * 当前默认实现从数据库查询，未来可扩展为接入天眼查/企查查/工商 API 等外部数据源。</p>
 *
 * <h3>实现约定</h3>
 * <ul>
 *   <li>默认实现：{@link DatabaseBidBackgroundLoader}（从 DB 查询）</li>
 *   <li>扩展实现：可注入多个实现类，由调用方按需选择</li>
 * </ul>
 *
 * @see DatabaseBidBackgroundLoader
 */
public interface BidBackgroundLoader {

    /**
     * 加载指定供应商的完整背景信息。
     *
     * @param supplierId 供应商 ID（对应 {@code supplier_detail.supplier_id}）
     * @return 背景信息文本（Markdown 格式），无数据时返回空字符串
     */
    String loadBackground(Integer supplierId);
}
