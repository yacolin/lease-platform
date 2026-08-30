package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.trd.dto.BalanceTransactionVO;
import com.example.leaseplatform.trd.entity.TrdBalanceTransaction;
import com.example.leaseplatform.trd.mapper.TrdBalanceTransactionMapper;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 余额账本：余额 + 赠送余额的增减（一律走流水，事务内更新用户余额并插入 trd_balance_transactions）。
 * 供充值（P2）、订单消费/退款（P3）等复用。
 * transaction_type：1-充值, 2-消费, 3-退款, 4-赠送, 5-调整。
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    public static final int TX_RECHARGE = 1;
    public static final int TX_CONSUME = 2;
    public static final int TX_REFUND = 3;
    public static final int TX_GIFT = 4;
    public static final int TX_ADJUST = 5;

    private final UsrUserMapper userMapper;
    private final TrdBalanceTransactionMapper txMapper;

    /**
     * 入账：余额增加 balanceDelta、赠送余额增加 giftDelta（充值/退款/赠送用）。
     * 流水 amount = 两项之和（正数）。
     */
    @Transactional
    public void credit(Long userId, BigDecimal balanceDelta, BigDecimal giftDelta, int type,
                       Long relatedOrderId, Long relatedRechargeId, String remark) {
        apply(userId, balanceDelta, giftDelta,
                balanceDelta.add(giftDelta), type, relatedOrderId, relatedRechargeId, remark);
    }

    /**
     * 出账：消费扣减 totalAmount，赠送余额优先扣，不足再扣余额（P3 订单支付用）。
     * 流水 amount = -totalAmount。
     */
    @Transactional
    public void debit(Long userId, BigDecimal totalAmount, Long relatedOrderId, String remark) {
        UsrUser user = requireUser(userId);
        BigDecimal balance = nz(user.getBalance());
        BigDecimal gift = nz(user.getGiftBalance());
        BigDecimal remaining = totalAmount;
        BigDecimal giftDelta = gift.min(remaining).negate();
        remaining = remaining.subtract(giftDelta.negate());
        BigDecimal balanceDelta = remaining.negate();
        if (balance.add(balanceDelta).signum() < 0) {
            throw BizException.conflict("余额不足");
        }
        apply(userId, balanceDelta, giftDelta, totalAmount.negate(),
                TX_CONSUME, relatedOrderId, null, remark);
    }

    /** 我的余额流水（分页，倒序） */
    public PageResult<BalanceTransactionVO> myTransactions(Long userId, int page, int size) {
        Page<TrdBalanceTransaction> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        txMapper.selectPage(p, new LambdaQueryWrapper<TrdBalanceTransaction>()
                .eq(TrdBalanceTransaction::getUserId, userId)
                .orderByDesc(TrdBalanceTransaction::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    /** 统一记账：更新用户余额 + 插流水（同事务） */
    @Transactional
    protected void apply(Long userId, BigDecimal balanceDelta, BigDecimal giftDelta, BigDecimal amount,
                         int type, Long relatedOrderId, Long relatedRechargeId, String remark) {
        UsrUser user = requireUser(userId);
        BigDecimal balanceBefore = nz(user.getBalance());
        BigDecimal giftBefore = nz(user.getGiftBalance());
        BigDecimal balanceAfter = balanceBefore.add(balanceDelta);
        BigDecimal giftAfter = giftBefore.add(giftDelta);
        if (balanceAfter.signum() < 0 || giftAfter.signum() < 0) {
            throw BizException.conflict("余额不足");
        }
        user.setBalance(balanceAfter);
        user.setGiftBalance(giftAfter);
        userMapper.updateById(user);

        TrdBalanceTransaction tx = new TrdBalanceTransaction();
        tx.setUserId(userId);
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setGiftBalanceBefore(giftBefore);
        tx.setGiftBalanceAfter(giftAfter);
        tx.setRelatedOrderId(relatedOrderId);
        tx.setRelatedRechargeId(relatedRechargeId);
        tx.setRemark(remark);
        txMapper.insert(tx);
    }

    private UsrUser requireUser(Long id) {
        UsrUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
        vo.setRelatedOrderId(t.getRelatedOrderId());
        vo.setRelatedRechargeId(t.getRelatedRechargeId());
        vo.setRemark(t.getRemark());
        vo.setCreatedAt(TimeUtil.toEpochMillis(t.getCreatedAt()));
        return vo;
    }
}
