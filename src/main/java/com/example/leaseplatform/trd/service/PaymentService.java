package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.trd.dto.PaymentVO;
import com.example.leaseplatform.trd.entity.TrdPayment;
import com.example.leaseplatform.trd.mapper.TrdPaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务（trd_payments，1.2）：统一支付事实模型。
 * <p>
 * 业务域（充值/订单/预订/会员购买）只保存业务事实；支付成功/退款统一落到支付单：
 * <pre>
 *   业务单（biz_type + biz_id）
 *        ↓
 *   支付单（创建时待支付）→ 支付成功（status 0→1 乐观更新，幂等）→ 完成
 *        ↓
 *   退款单（全额/部分/多次，关联支付单）
 * </pre>
 * 支付成功用「status=0 条件乐观更新」保证幂等：重复回调/并发回调不会重复入账
 * （roadmap 1.2「支付幂等」）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    // ---- 业务类型（与 trd_payments.biz_type 对齐） ----
    public static final int BIZ_RECHARGE = 1;          // 充值
    public static final int BIZ_ORDER = 2;             // 咖啡订单
    public static final int BIZ_MEAL_RESERVATION = 3;  // 正餐预订
    public static final int BIZ_MEMBER_PURCHASE = 4;   // 会员购买
    public static final int BIZ_MEETING = 5;           // 会议室订单（1.4）

    // ---- 支付方式 ----
    public static final int METHOD_BALANCE = 1;        // 余额支付
    public static final int METHOD_WECHAT = 2;         // 微信支付

    // ---- 支付渠道 ----
    public static final int CHANNEL_WECHAT_JSAPI = 1;  // 微信 JSAPI
    public static final int CHANNEL_BALANCE = 2;       // 余额
    public static final int CHANNEL_MOCK = 3;          // mock 直充（开发）

    // ---- 支付状态 ----
    public static final int STATUS_PENDING = 0;         // 待支付
    public static final int STATUS_SUCCESS = 1;         // 成功
    public static final int STATUS_FAILED = 2;          // 失败
    public static final int STATUS_PARTIAL_REFUNDED = 3;// 部分退款
    public static final int STATUS_REFUNDED = 4;        // 已退款

    private final TrdPaymentMapper paymentMapper;

    /** 创建支付单（待支付）；out_trade_no 由业务域生成并保证唯一 */
    @Transactional
    public TrdPayment create(Long userId, int bizType, Long bizId, long amount,
                             int paymentMethod, int paymentChannel, String outTradeNo) {
        TrdPayment payment = new TrdPayment();
        payment.setPaymentNo(generateNo("PAY"));
        payment.setUserId(userId);
        payment.setBizType(bizType);
        payment.setBizId(bizId);
        payment.setAmount(amount);
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentChannel(paymentChannel);
        payment.setStatus(STATUS_PENDING);
        payment.setOutTradeNo(outTradeNo);
        paymentMapper.insert(payment);
        return payment;
    }

    /**
     * 支付成功（幂等）：status 0→1 乐观更新，返回是否本次变更。
     * 余额支付立即调用；微信支付由回调/查单调用。
     */
    @Transactional
    public boolean settle(Long paymentId, String transactionId) {
        int updated = paymentMapper.update(null, new LambdaUpdateWrapper<TrdPayment>()
                .eq(TrdPayment::getId, paymentId)
                .eq(TrdPayment::getStatus, STATUS_PENDING)
                .set(TrdPayment::getStatus, STATUS_SUCCESS)
                .set(TrdPayment::getTransactionId, transactionId)
                .set(TrdPayment::getPaidAt, LocalDateTime.now()));
        return updated > 0;
    }

    /** 更新支付单金额（改期等场景；仅待支付状态可改，已结算不可改） */
    @Transactional
    public boolean updateAmount(Long paymentId, long amount) {
        return paymentMapper.update(null, new LambdaUpdateWrapper<TrdPayment>()
                .eq(TrdPayment::getId, paymentId)
                .eq(TrdPayment::getStatus, STATUS_PENDING)
                .set(TrdPayment::getAmount, amount)) > 0;
    }

    /** 按商户订单号结算（幂等；支付单不存在时返回 false 并告警，兼容历史数据） */
    @Transactional
    public boolean settleByOutTradeNo(String outTradeNo, String transactionId) {
        TrdPayment payment = getByOutTradeNo(outTradeNo);
        if (payment == null) {
            log.warn("支付单不存在，跳过结算：outTradeNo={}", outTradeNo);
            return false;
        }
        return settle(payment.getId(), transactionId);
    }

    /** 按商户订单号查支付单 */
    public TrdPayment getByOutTradeNo(String outTradeNo) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<TrdPayment>()
                .eq(TrdPayment::getOutTradeNo, outTradeNo));
    }

    /** 按业务单查支付单（一个业务单只允许一个有效支付单） */
    public TrdPayment getByBiz(int bizType, Long bizId) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<TrdPayment>()
                .eq(TrdPayment::getBizType, bizType)
                .eq(TrdPayment::getBizId, bizId)
                .orderByAsc(TrdPayment::getId)
                .last("LIMIT 1"));
    }

    /** 我的支付单（分页，可选业务类型筛选） */
    public PageResult<PaymentVO> myPayments(Long userId, int page, int size, Integer bizType) {
        Page<TrdPayment> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        paymentMapper.selectPage(p, new LambdaQueryWrapper<TrdPayment>()
                .eq(TrdPayment::getUserId, userId)
                .eq(bizType != null, TrdPayment::getBizType, bizType)
                .orderByDesc(TrdPayment::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    /** 管理端支付单分页（编号/业务类型/状态筛选） */
    public PageResult<PaymentVO> adminPage(int page, int size, String paymentNo,
                                           Integer bizType, Integer status) {
        Page<TrdPayment> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        paymentMapper.selectPage(p, new LambdaQueryWrapper<TrdPayment>()
                .like(paymentNo != null && !paymentNo.isBlank(), TrdPayment::getPaymentNo, paymentNo)
                .eq(bizType != null, TrdPayment::getBizType, bizType)
                .eq(status != null, TrdPayment::getStatus, status)
                .orderByDesc(TrdPayment::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private PaymentVO toVO(TrdPayment p) {
        PaymentVO vo = new PaymentVO();
        vo.setId(p.getId());
        vo.setPaymentNo(p.getPaymentNo());
        vo.setUserId(p.getUserId());
        vo.setBizType(p.getBizType());
        vo.setBizId(p.getBizId());
        vo.setAmount(p.getAmount());
        vo.setPaymentMethod(p.getPaymentMethod());
        vo.setPaymentChannel(p.getPaymentChannel());
        vo.setStatus(p.getStatus());
        vo.setOutTradeNo(p.getOutTradeNo());
        vo.setTransactionId(p.getTransactionId());
        vo.setPaidAt(TimeUtil.toEpochMillis(p.getPaidAt()));
        vo.setCreatedAt(TimeUtil.toEpochMillis(p.getCreatedAt()));
        return vo;
    }
}
