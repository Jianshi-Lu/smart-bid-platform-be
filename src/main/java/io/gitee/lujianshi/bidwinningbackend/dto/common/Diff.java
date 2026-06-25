package io.gitee.lujianshi.bidwinningbackend.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PATCH 更新前后的实体对比包装。{@code T} 为实体类型。
 *
 * @param <T> 实体类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Diff<T> {

    /** 修改前的实体（DB 原值）。 */
    private T before;

    /** 修改后的实体（请求体新值 + path id）。 */
    private T after;
}
