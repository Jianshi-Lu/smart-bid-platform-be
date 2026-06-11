package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoCreateRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经办人 Controller 层 — HTTP 入口
 *
 * <h3>链路位置（前端请求视角，从前到后）</h3>
 * <pre>
 *   前端 POST JSON
 *     ↓ Jackson 反序列化
 *   DTO (BidHandlerInfoCreateRequest)
 *     ↓ 字段拷贝
 *   Controller（本类，createBidHandlerInfo）
 *     ↓ Service 调用
 *   Service (BidHandlerInfoService.create)
 *     ↓ Mapper 调用
 *   Mapper (BidHandlerInfoMapper.insert)
 *     ↓ XML 映射
 *   SQL (BidHandlerInfoMapper.xml &lt;insert&gt;)
 *     ↓ JDBC
 *   MySQL bid_handler_info 表
 * </pre>
 *
 * <h3>链路位置（后端开发视角，从下到上）</h3>
 * <pre>
 *   ① bid_handler_info 表
 *      ↓
 *   ② BidHandlerInfo Entity
 *      ↓
 *   ③ BidHandlerInfoMapper 接口
 *      ↓
 *   ④ BidHandlerInfoMapper.xml SQL
 *      ↓
 *   ⑤ BidHandlerInfoService
 *      ↓
 *   ⑥ BidHandlerInfoCreateRequest DTO
 *      ↓
 *   ⑦ 本 Controller
 * </pre>
 *
 * <h3>已实现端点</h3>
 * <ul>
 *   <li>{@code POST /create-bid-handler-info} — 创建经办人</li>
 * </ul>
 *
 * <h3>待实现端点（删/改/查）</h3>
 * <ul>
 *   <li>{@code DELETE /bid-handler-info/{id}} — 按主键删除</li>
 *   <li>{@code PUT    /bid-handler-info/{id}} — 按主键更新</li>
 *   <li>{@code GET    /bid-handler-info/{id}} — 按主键单条查询</li>
 *   <li>{@code GET    /bid-handler-info}        — 列表查询</li>
 * </ul>
 *
 * <h3>待办</h3>
 * <ul>
 *   <li>实现 4 个待实现端点（删/改/查）</li>
 *   <li>当前 {@code createBidHandlerInfo} 未加 {@code @Valid} 校验，DTO 上的 {@code @NotBlank}/{@code @Size} 暂未生效</li>
 *   <li>统一异常处理（{@code GlobalExceptionHandler} 尚未实现）</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoCreateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo
 * @see io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService
 */
@RestController
public class BidHandlerInfoController {

    @Resource
    private BidHandlerInfoService service;

    /**
     * 创建经办人
     *
     * <p>执行链路：</p>
     * <pre>
     *   POST /create-bid-handler-info（Content-Type: application/json）
     *     → Jackson 反序列化 JSON → BidHandlerInfoCreateRequest
     *     → DTO 字段拷贝到 Entity
     *     → service.create(entity)
     *         → mapper.insert(entity) → SQL INSERT → MySQL 自增回填 handlerId
     *     → 返回 Entity（含 handlerId）序列化为 JSON
     * </pre>
     *
     * @param request 前端传入的经办人创建参数（{@code @NotBlank}/{@code @Size} 校验定义在 DTO 上，**当前未启用 @Valid，需后续补充**）
     * @return 包含 DB 自增 ID 的 Entity 对象（Jackson 自动序列化为 JSON）
     */
    @PostMapping("/create-bid-handler-info")
    public BidHandlerInfo createBidHandlerInfo(@RequestBody BidHandlerInfoCreateRequest request) {
        BidHandlerInfo entity = new BidHandlerInfo();
        entity.setHandlerName(request.getHandlerName());
        entity.setCompanyName(request.getCompanyName());
        entity.setDepartmentName(request.getDepartmentName());
        service.create(entity);

        return entity;
    }

}
