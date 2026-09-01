package com.example.leaseplatform.trd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账户（acct_accounts，1.5 账户/钱包）：余额唯一事实源（当前状态）。
 * <pre>
 *   User（usr_users，基础资料，不再持有余额）
 *     ↓
 *   Account（acct_accounts：可用/赠送/冻结）＝ 当前状态
 *     ↓
 *   Ledger（trd_balance_transactions）＝ 历史事实
 * </pre>
 * 账户 ID = 用户 ID（1:1）；available_balance 可用余额 / gift_balance 赠送余额 /
 * frozen_balance 冻结余额（押金/预授权/待结算/退款处理中）。
 * 余额变动统一走 {@code AccountService}（行锁 + 流水，保证并发与可审计）。
 */
@Data
@TableName("acct_accounts")
public class AcctAccount {

    /** 账户状态：0-冻结, 1-正常 */
    public static final int STATUS_FROZEN = 0;
    public static final int STATUS_NORMAL = 1;

    /** 账户 ID（= 用户 ID，1:1 账户，无自增） */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 可用余额（分） */
    private Long availableBalance;

    /** 赠送余额（分） */
    private Long giftBalance;

    /** 冻结余额（分） */
    private Long frozenBalance;

    /** 状态：0-冻结, 1-正常 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
