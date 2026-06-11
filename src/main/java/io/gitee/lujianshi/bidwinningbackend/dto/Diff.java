package io.gitee.lujianshi.bidwinningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 diff 包装类 — 用于展示 update 操作前后的实体对比
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>前端可直观看到"修改前 → 修改后"的完整数据</li>
 *   <li>泛型 {@code T} 适配任意实体（{@code BidHandlerInfo} / 未来的其他表）</li>
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
 *   Controller (update)
 *     → service.getById(id)        ← DB 原值
 *     → service.update(newEntity)  ← DB 写新值
 *     → 包装 Diff&lt;before, after&gt;
 *     → Result.success(diff, "更新成功")
 * </pre>
 *
 * @param <T> 实体类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Diff<T> {

    /**
     * 修改前的实体（DB 原值）
     */
    private T before;

    /**
     * 修改后的实体（请求体新值 + path id）
     */
    private T after;
}
