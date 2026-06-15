package io.gitee.lujianshi.bidwinningbackend.dto.common;

import io.gitee.lujianshi.bidwinningbackend.controller.bidhandlerinfo.BidHandlerInfoController;
import io.gitee.lujianshi.bidwinningbackend.controller.bidwinningnotification.BidWinningNotificationController;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 diff 包装类 — 用于展示 update 操作前后的实体对比
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>前端可直观看到"修改前 → 修改后"的完整数据</li>
 *   <li>泛型 {@code T} 适配任意实体（{@code BidHandlerInfo} / {@code BidWinningNotification} / 未来的其他表）</li>
 *   <li>无业务方法，纯数据载体</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   {
 *     "before": { "handlerId": 1, "handlerName": "张三", ... },
 *     "after":  { "handlerId": 1, "handlerName": "李四", ... }
 *   }
 * </pre>
 *
 * <h3>链路位置</h3>
 * <pre>
 *   Controller (PATCH update)
 *     → service.getById(id)         ← diff.before 来源（DB 原值）
 *     → 构造新 entity（id + 字段，null 字段不更新）
 *     → service.update(newEntity)   ← diff.after 写入 DB
 *     → 包装 Diff&lt;before, after&gt;
 *     → Result.success(diff, "更新成功")
 * </pre>
 *
 * <h3>为什么用泛型而不是具体类型？</h3>
 * <ul>
 *   <li>不同实体的 PATCH 端点可共用同一 {@code Diff<T>}</li>
 *   <li>新增表无需新建 Diff 子类</li>
 *   <li>Jackson 序列化时根据 {@code T} 推断具体类型</li>
 * </ul>
 *
 * <h3>当前使用范围</h3>
 * <ul>
 *   <li>{@code BidHandlerInfoController.patchBidHandlerInfo}</li>
 *   <li>{@code BidWinningNotificationController.patchBidWinningNotification}</li>
 *   <li>未来其他 Controller 的 update 端点</li>
 * </ul>
 *
 * @param <T> 实体类型
 *
 * @see io.gitee.lujianshi.bidwinningbackend.dto.common.Result
 * @see BidHandlerInfoController
 * @see BidWinningNotificationController
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Diff<T> {

    /**
     * 修改前的实体（DB 原值）
     * <p>来源：{@code service.getById(id)} 返回的实体快照</p>
     */
    private T before;

    /**
     * 修改后的实体（请求体新值 + path id）
     * <p>来源：Controller 构造的 entity（PATCH 端点传入的字段，未传字段为 null）</p>
     */
    private T after;
}
