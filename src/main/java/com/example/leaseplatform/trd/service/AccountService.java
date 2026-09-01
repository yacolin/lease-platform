package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.trd.dto.BalanceTransactionVO;
import com.example.leaseplatform.trd.entity.AcctAccount;
import com.example.leaseplatform.trd.entity.TrdBalanceTransaction;
import com.example.leaseplatform.trd.mapper.AcctAccountMapper;
import com.example.leaseplatform.trd.mapper.TrdBalanceTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账户账本服务（1.5 账户/钱包）：余额唯一事实源为 acct_accounts（当前状态），
 * 所有变动写 trd_balance_transactions（历史事实），形成
 * User → Account → Balance → Ledger → Payment → Refund 完整链路。
 * <p>
 * - 行锁（acct_accounts SELECT ... FOR UPDATE）串行化同账户并发余额变动；
 * - 消费策略（1.5.5）：赠送余额优先扣，不足扣可用余额（混合扣款）；
 * - 冻结/解冻（1.5.3）：押金/预授权/待结算/退款处理中，走 TX_FREEZE/TX_UNFREEZE 流水；
 * - 资金来源（1.5.4）：充值余额与赠送余额已区分；活动/退款余额按需逐步引入，不过度拆分。
 * transaction_type：1-充值, 2-消费, 3-退款, 4-赠送, 5-调整, 6-冻结, 7-解冻。
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    public static final int TX_RECHARGE = 1;
    public static final int TX_CONSUME = 2;
    public static final int TX_REFUND = 3;
    public static final int TX_GIFT = 4;
    public static final int TX_ADJUST = 5;
    public static final int TX_FREEZE = 6;    // 冻结：可用余额 → 冻结余额
    public static final int TX_UNFREEZE = 7;  // 解冻：冻结余额 → 可用余额

    private final AcctAccountMapper accountMapper;
    private final TrdBalanceTransactionMapper txMapper;

    /** 入账：可用余额增加 balanceDelta、赠送余额增加 giftDelta（充值/退款/赠送用） */
    @Transactional
    public void credit(Long userId, long balanceDelta, long giftDelta, int type,
                       Long relatedOrderId, Long relatedRechargeId, String remark) {
        apply(userId, balanceDelta, giftDelta, 0L, balanceDelta + giftDelta,
                type, relatedOrderId, relatedRechargeId, remark);
    }

    /**
     * 出账：消费扣减 totalAmount，赠送余额优先扣，不足再扣可用余额。
     * 流水 amount = -totalAmount。
     */
    @Transactional
    public void debit(Long userId, long totalAmount, Long relatedOrderId, String remark) {
        AcctAccount account = lockAccount(userId);
        long available = nz(account.getAvailableBalance());
        long gift = nz(account.getGiftBalance());
        long giftUsed = Math.min(gift, totalAmount);
        long availableUsed = totalAmount - giftUsed;
        if (available < availableUsed) {
            throw BizException.conflict("余额不足");
        }
        applyLocked(account, -availableUsed, -giftUsed, 0L, -totalAmount,
                TX_CONSUME, relatedOrderId, null, remark);
    }

    /** 冻结（押金/预授权/待结算）：可用余额 → 冻结余额 */
    @Transactional
    public void freeze(Long userId, long amount, Long relatedOrderId, String remark) {
        if (amount <= 0) {
            throw BizException.badRequest("冻结金额必须为正");
        }
        AcctAccount account = lockAccount(userId);
        if (nz(account.getAvailableBalance()) < amount) {
            throw BizException.conflict("余额不足，无法冻结");
        }
        applyLocked(account, -amount, 0L, amount, -amount,
                TX_FREEZE, relatedOrderId, null, remark);
    }

    /** 解冻：冻结余额 → 可用余额 */
    @Transactional
    public void unfreeze(Long userId, long amount, Long relatedOrderId, String remark) {
        if (amount <= 0) {
            throw BizException.badRequest("解冻金额必须为正");
        }
        AcctAccount account = lockAccount(userId);
        if (nz(account.getFrozenBalance()) < amount) {
            throw BizException.conflict("冻结余额不足");
        }
        applyLocked(account, amount, 0L, -amount, amount,
                TX_UNFREEZE, relatedOrderId, null, remark);
    }

    /** 查询账户（懒创建：不存在则建 0 余额账户，兼容新注册用户/历史数据） */
    public AcctAccount getOrCreate(Long userId) {
        AcctAccount account = accountMapper.selectOne(new LambdaQueryWrapper<AcctAccount>()
                .eq(AcctAccount::getUserId, userId));
        return account == null ? createAccount(userId) : account;
    }

    /** 我的余额流水（分页，倒序） */
    public PageResult<BalanceTransactionVO> myTransactions(Long userId, int page, int size) {
        Page<TrdBalanceTransaction> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        txMapper.selectPage(p, new LambdaQueryWrapper<TrdBalanceTransaction>()
                .eq(TrdBalanceTransaction::getUserId, userId)
                .orderByDesc(TrdBalanceTransaction::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    /** 统一记账（懒建账户）：行锁 → 更新账户 → 插流水（同事务） */
    @Transactional
    protected void apply(Long userId, long balanceDelta, long giftDelta, long frozenDelta, long amount,
                         int type, Long relatedOrderId, Long relatedRechargeId, String remark) {
        AcctAccount account = lockAccount(userId);
        applyLocked(account, balanceDelta, giftDelta, frozenDelta, amount,
                type, relatedOrderId, relatedRechargeId, remark);
    }

    /** 统一记账（账户已锁定）：更新账户 + 插流水 */
    private void applyLocked(AcctAccount account, long balanceDelta, long giftDelta, long frozenDelta,
                             long amount, int type, Long relatedOrderId, Long relatedRechargeId,
                             String remark) {
        long availableBefore = nz(account.getAvailableBalance());
        long giftBefore = nz(account.getGiftBalance());
        long frozenBefore = nz(account.getFrozenBalance());
        long availableAfter = availableBefore + balanceDelta;
        long giftAfter = giftBefore + giftDelta;
        long frozenAfter = frozenBefore + frozenDelta;
        if (availableAfter < 0 || giftAfter < 0 || frozenAfter < 0) {
            throw BizException.conflict("余额不足");
        }
        account.setAvailableBalance(availableAfter);
        account.setGiftBalance(giftAfter);
        account.setFrozenBalance(frozenAfter);
        accountMapper.updateById(account);

        TrdBalanceTransaction tx = new TrdBalanceTransaction();
        tx.setUserId(account.getUserId());
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setBalanceBefore(availableBefore);
        tx.setBalanceAfter(availableAfter);
        tx.setGiftBalanceBefore(giftBefore);
        tx.setGiftBalanceAfter(giftAfter);
        tx.setFrozenBalanceBefore(frozenBefore);
        tx.setFrozenBalanceAfter(frozenAfter);
        tx.setRelatedOrderId(relatedOrderId);
        tx.setRelatedRechargeId(relatedRechargeId);
        tx.setRemark(remark);
        txMapper.insert(tx);
    }

    /** 行锁：SELECT ... FOR UPDATE 串行化同账户并发余额变动（交易一致性） */
    private AcctAccount lockAccount(Long userId) {
        AcctAccount account = accountMapper.selectOne(new LambdaQueryWrapper<AcctAccount>()
                .eq(AcctAccount::getUserId, userId)
                .last("FOR UPDATE"));
        return account == null ? createAccount(userId) : account;
    }

    private AcctAccount createAccount(Long userId) {
        AcctAccount account = new AcctAccount();
        account.setId(userId);
        account.setUserId(userId);
        account.setAvailableBalance(0L);
        account.setGiftBalance(0L);
        account.setFrozenBalance(0L);
        account.setStatus(AcctAccount.STATUS_NORMAL);
        try {
            accountMapper.insert(account);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发懒创建：返回既有账户
            return accountMapper.selectOne(new LambdaQueryWrapper<AcctAccount>()
                    .eq(AcctAccount::getUserId, userId));
        }
        return account;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private BalanceTransactionVO toVO(TrdBalanceTransaction t) {
        BalanceTransactionVO vo = new BalanceTransactionVO();
        vo.setId(t.getId());
        vo.setTransactionType(t.getTransactionType());
        vo.setAmount(t.getAmount());
        vo.setBalanceBefore(t.getBalanceBefore());
        vo.setBalanceAfter(t.getBalanceAfter());
        vo.setGiftBalanceBefore(t.getGiftBalanceBefore());
        vo.setGiftBalanceAfter(t.getGiftBalanceAfter());
        vo.setFrozenBalanceBefore(t.getFrozenBalanceBefore());
        vo.setFrozenBalanceAfter(t.getFrozenBalanceAfter());
        vo.setRelatedOrderId(t.getRelatedOrderId());
        vo.setRelatedRechargeId(t.getRelatedRechargeId());
        vo.setRemark(t.getRemark());
        vo.setCreatedAt(TimeUtil.toEpochMillis(t.getCreatedAt()));
        return vo;
    }
}
