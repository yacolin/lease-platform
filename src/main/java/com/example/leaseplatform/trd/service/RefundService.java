package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.trd.dto.RefundVO;
import com.example.leaseplatform.trd.entity.TrdPayment;
import com.example.leaseplatform.trd.entity.TrdRefund;
import com.example.leaseplatform.trd.mapper.TrdPaymentMapper;
import com.example.leaseplatform.trd.mapper.TrdRefundMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 退款服务（trd_refunds，1.2）：全额/部分/多次退款，必须关联支付单。
 * <p>
 * 校验（roadmap 1.2.5 交易一致性）：
 * <ul>
 *   <li>支付单必须已支付成功（待支付/失败不可退款）；</li>
 *   <li>退款总额不允许超过支付金额（已退金额 + 本次退款 ≤ 支付金额）；</li>
 *   <li>幂等：idempotency_key 唯一，同一业务取消/商家退款重复执行只产生一笔退款单。</li>
 * </ul>
 * 余额退款同步完成（入账 + 标记成功）；微信退款（原路微信）为异步渠道，1.2 预留字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    // ---- 退款方式 ----
    public static final int METHOD_BALANCE = 1;  // 原路余额
    public static final int METHOD_WECHAT = 2;   // 原路微信（异步渠道，预留）

    // ---- 退款状态 ----
    public static final int STATUS_PROCESSING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    private final TrdRefundMapper refundMapper;
    private final TrdPaymentMapper paymentMapper;
    private final BalanceService balanceService;

    /**
     * 原路余额退款（同步）：创建退款单 → 校验 → 余额入账 → 标记成功 → 更新支付单状态。
     *
     * @param userId         退款用户 ID
     * @param paymentId      关联支付单 ID
     * @param amount         退款金额（分）
     * @param reason         退款原因
     * @param idempotencyKey 幂等键（同一业务场景只退一次；重复请求返回既有退款单）
     * @param relatedOrderId 余额流水关联业务单 ID（余额流水 related_order_id，可为空）
     * @return 退款单（已存在的幂等返回既有单）
     */
    @Transactional
    public TrdRefund refundToBalance(Long userId, Long paymentId, long amount, String reason,
                                     String idempotencyKey, Long relatedOrderId) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            TrdRefund existing = refundMapper.selectOne(new LambdaQueryWrapper<TrdRefund>()
                    .eq(TrdRefund::getIdempotencyKey, idempotencyKey));
            if (existing != null) {
                return existing; // 幂等：重复请求直接返回
            }
        }
        TrdPayment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw BizException.notFound("支付单不存在");
        }
        if (payment.getStatus() == null
                || (payment.getStatus() != PaymentService.STATUS_SUCCESS
                && payment.getStatus() != PaymentService.STATUS_PARTIAL_REFUNDED)) {
            throw BizException.conflict("支付单不可退款");
        }
        long refunded = refundedAmount(paymentId);
        long remaining = payment.getAmount() - refunded;
        if (amount <= 0 || amount > remaining) {
            throw BizException.badRequest("退款金额超出可退金额（可退 " + remaining + " 分）");
        }

        TrdRefund refund = new TrdRefund();
        refund.setRefundNo(generateNo("RF"));
        refund.setPaymentId(paymentId);
        refund.setUserId(userId);
        refund.setBizType(payment.getBizType());
        refund.setBizId(payment.getBizId());
        refund.setRefundAmount(amount);
        refund.setRefundMethod(METHOD_BALANCE);
        refund.setStatus(STATUS_SUCCESS);
        refund.setIdempotencyKey(idempotencyKey);
        refund.setRefundReason(reason);
        refund.setRefundedAt(LocalDateTime.now());
        try {
            refundMapper.insert(refund);
        } catch (DuplicateKeyException e) {
            // 并发同幂等键：返回已有退款单（唯一键兜底）
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                TrdRefund concurrent = refundMapper.selectOne(new LambdaQueryWrapper<TrdRefund>()
                        .eq(TrdRefund::getIdempotencyKey, idempotencyKey));
                if (concurrent != null) {
                    return concurrent;
                }
            }
            throw e;
        }
        // 余额原路退回 + 流水（同事务；1.5 账户模型前赠送余额不回补，保持 1.1 行为）
        balanceService.credit(userId, amount, 0L, BalanceService.TX_REFUND,
                relatedOrderId != null ? relatedOrderId : payment.getBizId(), null, reason);
        // 更新支付单状态：累计退款 ≥ 支付金额 → 已退款；否则 → 部分退款
        long totalRefunded = refunded + amount;
        int paymentStatus = totalRefunded >= payment.getAmount()
                ? PaymentService.STATUS_REFUNDED : PaymentService.STATUS_PARTIAL_REFUNDED;
        paymentMapper.update(null, new LambdaUpdateWrapper<TrdPayment>()
                .eq(TrdPayment::getId, paymentId)
                .set(TrdPayment::getStatus, paymentStatus));
        return refund;
    }

    /** 已成功退款总额（分） */
    public long refundedAmount(Long paymentId) {
        return refundMapper.selectList(new LambdaQueryWrapper<TrdRefund>()
                        .select(TrdRefund::getRefundAmount)
                        .eq(TrdRefund::getPaymentId, paymentId)
                        .eq(TrdRefund::getStatus, STATUS_SUCCESS))
                .stream().mapToLong(r -> r.getRefundAmount() == null ? 0L : r.getRefundAmount()).sum();
    }

    /** 我的退款单（分页） */
    public PageResult<RefundVO> myRefunds(Long userId, int page, int size) {
        Page<TrdRefund> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        refundMapper.selectPage(p, new LambdaQueryWrapper<TrdRefund>()
                .eq(TrdRefund::getUserId, userId)
                .orderByDesc(TrdRefund::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    /** 管理端退款单分页（编号/状态筛选） */
    public PageResult<RefundVO> adminPage(int page, int size, String refundNo, Integer status) {
        Page<TrdRefund> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        refundMapper.selectPage(p, new LambdaQueryWrapper<TrdRefund>()
                .like(refundNo != null && !refundNo.isBlank(), TrdRefund::getRefundNo, refundNo)
                .eq(status != null, TrdRefund::getStatus, status)
                .orderByDesc(TrdRefund::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private RefundVO toVO(TrdRefund r) {
        RefundVO vo = new RefundVO();
        vo.setId(r.getId());
        vo.setRefundNo(r.getRefundNo());
        vo.setPaymentId(r.getPaymentId());
        vo.setUserId(r.getUserId());
        vo.setBizType(r.getBizType());
        vo.setBizId(r.getBizId());
        vo.setRefundAmount(r.getRefundAmount());
        vo.setRefundMethod(r.getRefundMethod());
        vo.setStatus(r.getStatus());
        vo.setRefundReason(r.getRefundReason());
        vo.setRefundedAt(TimeUtil.toEpochMillis(r.getRefundedAt()));
        vo.setCreatedAt(TimeUtil.toEpochMillis(r.getCreatedAt()));
        return vo;
    }
}
