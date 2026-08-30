package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.trd.dto.RechargeCreateResultVO;
import com.example.leaseplatform.trd.dto.RechargeRecordVO;
import com.example.leaseplatform.trd.entity.TrdRechargeRecord;
import com.example.leaseplatform.trd.entity.TrdRechargeTier;
import com.example.leaseplatform.trd.mapper.TrdRechargeRecordMapper;
import com.example.leaseplatform.trd.mapper.TrdRechargeTierMapper;
import com.example.leaseplatform.trd.wechat.WechatPayClient;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 充值服务（参照 campus_express 支付模式）：
 * - 下单 {@link #createRecharge}：创建待支付记录；微信支付已配置 → 额外 JSAPI 下单返回调起支付参数；
 *   未配置（开发默认）→ prepayParams 为空，前端走 mock-pay 直充；
 * - mock 直充 {@link #mockPay}（开发模式）；
 * - 微信支付回调 {@link #handleNotify} + 主动查单 {@link #query} 兜底；
 * - 入账统一走 {@link #settleRecharge}（事务 + 幂等：状态 0→1 乐观更新，成功后余额/赠送余额入账 + 流水）。
 */
@Service
@RequiredArgsConstructor
public class RechargeService {

    public static final int PAY_PENDING = 0;
    public static final int PAY_SUCCESS = 1;

    private static final int PAYMENT_METHOD_WECHAT = 1;

    private final TrdRechargeRecordMapper recordMapper;
    private final TrdRechargeTierMapper tierMapper;
    private final UsrUserMapper userMapper;
    private final BalanceService balanceService;
    private final WechatPayClient wechatPayClient;

    /** 充值下单：创建待支付记录（未配置微信支付时 prepayParams 为空，走 mock-pay） */
    @Transactional
    public RechargeCreateResultVO createRecharge(Long userId, Long tierId) {
        TrdRechargeTier tier = tierMapper.selectById(tierId);
        if (tier == null) {
            throw BizException.notFound("充值档位不存在");
        }
        if (tier.getStatus() == null || tier.getStatus() != 1) {
            throw BizException.badRequest("该充值档位已停用");
        }
        UsrUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }

        String outTradeNo = generateNo("RC");
        TrdRechargeRecord record = new TrdRechargeRecord();
        record.setUserId(userId);
        record.setTierId(tier.getId());
        record.setRechargeAmount(tier.getRechargeAmount());
        record.setBonusAmount(tier.getBonusAmount());
        record.setTotalAmount(tier.getActualAmount());
        record.setPaymentMethod(PAYMENT_METHOD_WECHAT);
        record.setOutTradeNo(outTradeNo);
        record.setPaymentStatus(PAY_PENDING);
        recordMapper.insert(record);

        RechargeCreateResultVO result = new RechargeCreateResultVO();
        result.setRecord(toVO(record));
        // 微信支付已配置 → JSAPI 下单返回调起支付参数；未配置 → 前端走 mock-pay
        // 金额已是整数「分」，直接传给微信（与 DB/API 全链路一致，无需换算）
        if (wechatPayClient.isConfigured()) {
            long amountFen = tier.getRechargeAmount();
            String prepayId = wechatPayClient.createJsapiOrder(
                    user.getOpenid(), outTradeNo, amountFen, "余额充值");
            result.setPrepayParams(buildJsapiPayParams(user.getOpenid(), prepayId));
        }
        return result;
    }

    /** 开发 mock 直充：立即入账（幂等） */
    @Transactional
    public RechargeRecordVO mockPay(Long userId, Long recordId) {
        TrdRechargeRecord record = requireOwnRecord(userId, recordId);
        return settleRecharge(record, "mock_" + record.getOutTradeNo());
    }

    /**
     * 微信支付 V3 回调处理：验签解密（WechatPayClient）→ 解析 out_trade_no / transaction_id →
     * trade_state=SUCCESS 时入账。幂等：重复回调不重复入账。
     */
    @Transactional
    public void handleNotify(String body) {
        if (!wechatPayClient.isConfigured()) {
            throw BizException.badRequest("微信支付未配置");
        }
        Map<?, ?> root = parseJson(body);
        if (!"TRANSACTION.SUCCESS".equals(root.get("event_type"))) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<?, ?> resource = (Map<?, ?>) root.get("resource");
        if (resource == null) {
            throw BizException.badRequest("微信回调缺少 resource");
        }
        String plain = wechatPayClient.decryptNotify(resource);
        Map<?, ?> data = parseJson(plain);
        if (!"SUCCESS".equals(data.get("trade_state"))) {
            return;
        }
        String outTradeNo = String.valueOf(data.get("out_trade_no"));
        String transactionId = String.valueOf(data.get("transaction_id"));
        TrdRechargeRecord record = recordMapper.selectOne(new LambdaQueryWrapper<TrdRechargeRecord>()
                .eq(TrdRechargeRecord::getOutTradeNo, outTradeNo));
        if (record == null) {
            throw BizException.notFound("充值记录不存在");
        }
        settleRecharge(record, transactionId);
    }

    /** 主动查单兜底：已配置微信支付时查微信侧，SUCCESS 则入账；返回最新状态 */
    @Transactional
    public RechargeRecordVO query(Long userId, Long recordId) {
        TrdRechargeRecord record = requireOwnRecord(userId, recordId);
        if (record.getPaymentStatus() == PAY_PENDING && wechatPayClient.isConfigured()) {
            Map<?, ?> resp = wechatPayClient.queryOrder(record.getOutTradeNo());
            if ("SUCCESS".equals(resp.get("trade_state"))) {
                return settleRecharge(record, String.valueOf(resp.get("transaction_id")));
            }
        }
        return toVO(record);
    }

    /** 我的充值记录（分页） */
    public PageResult<RechargeRecordVO> myRecords(Long userId, int page, int size) {
        Page<TrdRechargeRecord> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        recordMapper.selectPage(p, new LambdaQueryWrapper<TrdRechargeRecord>()
                .eq(TrdRechargeRecord::getUserId, userId)
                .orderByDesc(TrdRechargeRecord::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    /**
     * 支付成功入账（mock 直充 / 微信回调 / 查单共用）：事务 + 幂等。
     * 用 payment_status=0 条件做乐观更新，并发重复回调不会重复入账。
     */
    @Transactional
    public RechargeRecordVO settleRecharge(TrdRechargeRecord record, String transactionId) {
        if (record.getPaymentStatus() != null && record.getPaymentStatus() == PAY_SUCCESS) {
            return toVO(record); // 幂等
        }
        int updated = recordMapper.update(null, new LambdaUpdateWrapper<TrdRechargeRecord>()
                .eq(TrdRechargeRecord::getId, record.getId())
                .eq(TrdRechargeRecord::getPaymentStatus, PAY_PENDING)
                .set(TrdRechargeRecord::getPaymentStatus, PAY_SUCCESS)
                .set(TrdRechargeRecord::getPaidAt, LocalDateTime.now())
                .set(TrdRechargeRecord::getTransactionId, transactionId));
        if (updated == 0) {
            // 并发已处理：返回最新状态
            return toVO(recordMapper.selectById(record.getId()));
        }
        record.setPaymentStatus(PAY_SUCCESS);
        record.setTransactionId(transactionId);
        record.setPaidAt(LocalDateTime.now());
        // 余额 + 赠送余额入账 + 流水（同事务）
        balanceService.credit(record.getUserId(), record.getRechargeAmount(), record.getBonusAmount(),
                BalanceService.TX_RECHARGE, null, record.getId(), "余额充值");
        return toVO(record);
    }

    private TrdRechargeRecord requireOwnRecord(Long userId, Long recordId) {
        TrdRechargeRecord record = recordMapper.selectById(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw BizException.notFound("充值记录不存在");
        }
        return record;
    }

    /** 小程序调起支付参数（JSAPI 二次签名） */
    private RechargeCreateResultVO.PayParamsVO buildJsapiPayParams(String appId, String prepayId) {
        long timeStamp = Instant.now().getEpochSecond();
        String nonceStr = randomNonce();
        String packageValue = "prepay_id=" + prepayId;
        String message = appId + "\n" + timeStamp + "\n" + nonceStr + "\n" + packageValue + "\n";
        RechargeCreateResultVO.PayParamsVO params = new RechargeCreateResultVO.PayParamsVO();
        params.setTimeStamp(String.valueOf(timeStamp));
        params.setNonceStr(nonceStr);
        params.setPackageValue(packageValue);
        params.setSignType("RSA");
        params.setPaySign(wechatPayClient.signMessage(message));
        return params;
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private String randomNonce() {
        byte[] buf = new byte[16];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return (Map<String, Object>) new tools.jackson.databind.ObjectMapper()
                    .readValue(json, Map.class);
        } catch (Exception e) {
            throw BizException.badRequest("微信回调内容解析失败");
        }
    }

    private RechargeRecordVO toVO(TrdRechargeRecord r) {
        RechargeRecordVO vo = new RechargeRecordVO();
        vo.setId(r.getId());
        vo.setOutTradeNo(r.getOutTradeNo());
        vo.setTierId(r.getTierId());
        vo.setRechargeAmount(r.getRechargeAmount());
        vo.setBonusAmount(r.getBonusAmount());
        vo.setTotalAmount(r.getTotalAmount());
        vo.setPaymentMethod(r.getPaymentMethod());
        vo.setTransactionId(r.getTransactionId());
        vo.setPaymentStatus(r.getPaymentStatus());
        vo.setPaidAt(TimeUtil.toEpochMillis(r.getPaidAt()));
        vo.setCreatedAt(TimeUtil.toEpochMillis(r.getCreatedAt()));
        return vo;
    }
}
