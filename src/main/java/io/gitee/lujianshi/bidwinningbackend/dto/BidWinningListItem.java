package io.gitee.lujianshi.bidwinningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class BidWinningListItem {
    private String supplierName;
    private String bidWinningAmount;
}
