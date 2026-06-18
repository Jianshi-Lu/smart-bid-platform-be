package io.gitee.lujianshi.bidwinningbackend.dto.statuscheck;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 中标通知书邮件请求对象。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code to / from / subject / content} — 继承自 {@link PlainTextMailRequest}</li>
 *   <li>{@code projectCode} — 项目编号（模板填充变量）</li>
 *   <li>{@code handlerName} — 经办人姓名（模板填充变量）</li>
 *   <li>{@code bidWinningList} — 中标结果列表（th:each 循环渲染）</li>
 *   <li>{@code templateName} — 模板文件名（不含 .html 后缀）</li>
 * </ul>
 *
 * <p>使用方：
 * <ul>
 *   <li>{@code StatusCheckService.sendNotification(BidWinningMailRequest)}</li>
 *   <li>{@code StatusCheckController.sendNotification} 端点</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class BidWinningMailRequest extends PlainTextMailRequest {

    private String projectCode;
    private String handlerName;
    private List<BidWinningListItem> bidWinningList;
    private String templateName;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BidWinningListItem {
        private String supplierName;
        private String bidWinningAmount;
    }
}
