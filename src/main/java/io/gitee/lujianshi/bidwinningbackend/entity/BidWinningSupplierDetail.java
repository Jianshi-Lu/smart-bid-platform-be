package io.gitee.lujianshi.bidwinningbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid_winning_supplier_detail")
public class BidWinningSupplierDetail {
    @TableId(type = IdType.AUTO)
    private Integer detailId;
    private Integer notificationId;
    private String supplierName;
    private BigDecimal amount;

    private String targetEmail;
}