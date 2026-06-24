package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.service.SupplierDetailService;
import org.apache.ibatis.annotations.Mapper;

/**
 * 供应商明细 Mapper 接口 — 继承 {@link BaseMapper}，绑定 {@code SupplierDetailMapper.xml}。
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseMapper&lt;SupplierDetail&gt;
 *     ↑ extends
 *   SupplierDetailMapper（本接口）— namespace = mapper/SupplierDetailMapper.xml
 * </pre>
 *
 * <h3>对应数据库表 {@code supplier_detail}</h3>
 * <ul>
 *   <li>建表脚本：{@code src/main/resources/sql/ddl/V10__restructure_supplier_detail.sql}</li>
 *   <li>主键：{@code supplier_id}（自增），字段数：4</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail
 * @see SupplierDetailService
 */
@Mapper
public interface SupplierDetailMapper extends BaseMapper<SupplierDetail> {
}
