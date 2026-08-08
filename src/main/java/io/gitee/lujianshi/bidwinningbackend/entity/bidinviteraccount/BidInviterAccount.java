package io.gitee.lujianshi.bidwinningbackend.entity.bidinviteraccount;

import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 招标方（管理员）账号 — 数据库实体。对应表 {@code bid_inviter_account}（V39）。
 *
 * <p>与 {@link BidderAccount} 对称：HTTP Basic 实时校验，演示环境明文密码。
 * 招标方管理接口（公告/经办人/投标记录/风险分析/问答/知识文件）统一校验该账号。</p>
 *
 * @since V39
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bid_inviter_account")
public class BidInviterAccount {

    @TableId(type = IdType.AUTO)
    private Long bidInviterAccountId;

    private String username;

    private String password;

    private String phone;

    private String displayName;

    private LocalDateTime createdAt;
}
