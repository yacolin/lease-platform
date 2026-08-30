package com.example.leaseplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信支付配置（application.yml 的 wechat.pay.*）。
 * 开发期留空 = 未配置：充值下单不返回调起支付参数、回调 notify 不可用，
 * 前端走 mock-pay 直充（trd_recharge_records 直充入账）；生产配置后走真实 JSAPI。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.pay")
public class WechatPayProperties {

    /** 微信支付商户号 */
    private String mchId;

    /** 商户 API v3 密钥（回调 AES-256-GCM 解密用） */
    private String apiV3Key;

    /** 商户 API 证书私钥路径（PKCS8 PEM，JSAPI 下单签名用） */
    private String privateKeyPath;

    /** 商户 API 证书序列号 */
    private String mchSerialNo;

    /** 支付回调地址（微信服务器回调 /api/v1/wx/payments/notify 的公网地址） */
    private String notifyUrl;

    /** 是否已完整配置（全部必填项非空） */
    public boolean isConfigured() {
        return notBlank(mchId) && notBlank(apiV3Key) && notBlank(privateKeyPath)
                && notBlank(mchSerialNo) && notBlank(notifyUrl);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
