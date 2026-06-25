package io.gitee.lujianshi.bidwinningbackend.dto.statuscheck;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 中标通知书邮件请求对象。在 {@link PlainTextMailRequest} 基础上加 projectCode / handlerName / bidWinningList / templateName。
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
        private String tendererName;
        private String bidWinningAmount;
    }
}
