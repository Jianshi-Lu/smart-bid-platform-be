package io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 投标方账号 — 数据库实体。对应表 {@code bidder_account}（V36）。
 *
 * <p>投标方登录身份的根：{@code bid_record.bidder_account_id} 与资信文件链路都通过
 * 该账号完成归属与溯源。当前为演示环境最小实现，密码明文存储（V37），
 * 禁止用于生产。</p>
 *
 * @since V36
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bidder_account")
public class BidderAccount {

    @TableId(type = IdType.AUTO)
    private Long bidderAccountId;

    private String username;

    private String password;

    private String phone;

    private String displayName;

    private LocalDateTime createdAt;
}
