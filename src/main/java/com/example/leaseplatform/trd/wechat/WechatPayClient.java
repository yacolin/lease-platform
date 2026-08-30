package com.example.leaseplatform.trd.wechat;

import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.config.WechatPayProperties;
import com.example.leaseplatform.config.WechatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * 微信支付 V3 客户端（标准 JCA，无第三方依赖）：
 * - JSAPI 下单：POST /v3/pay/transactions/jsapi（WECHATPAY2-SHA256-RSA2048 签名）；
 * - 回调通知解密：AES-256-GCM（API v3 密钥）；
 * - 主动查单：GET /v3/pay/transactions/out-trade-no/{no}。
 * <p>
 * 未配置 wechat.pay.* 时（开发默认）：各方法抛 BizException"微信支付未配置"，
 * 充值走 mock-pay 直充；配置完整后自动启用真实支付（无需改业务代码）。
 */
@Slf4j
@Component
public class WechatPayClient {

    private static final String BASE_URL = "https://api.mch.weixin.qq.com";
    private static final String JSAPI_PATH = "/v3/pay/transactions/jsapi";
    private static final String SCHEME = "WECHATPAY2-SHA256-RSA2048";

    private final WechatPayProperties properties;
    private final WechatProperties wechatProperties;
    private final RestClient restClient = RestClient.create();

    public WechatPayClient(WechatPayProperties properties, WechatProperties wechatProperties) {
        this.properties = properties;
        this.wechatProperties = wechatProperties;
    }

    /** 是否已配置（决定走真实微信支付还是 mock 直充） */
    public boolean isConfigured() {
        return properties.isConfigured() && wechatProperties.getAppid() != null && !wechatProperties.getAppid().isBlank();
    }

    /**
     * JSAPI 下单：返回 prepay_id。
     *
     * @param openid     用户 openid
     * @param outTradeNo 商户订单号
     * @param amountFen  金额（分）
     * @param description 商品描述
     */
    public String createJsapiOrder(String openid, String outTradeNo, long amountFen, String description) {
        requireConfigured();
        String body = """
                {"appid":"%s","mchid":"%s","description":"%s","out_trade_no":"%s",
                 "notify_url":"%s","amount":{"total":%d,"currency":"CNY"},
                 "payer":{"openid":"%s"}}"""
                .formatted(appid(), properties.getMchId(), description, outTradeNo,
                        properties.getNotifyUrl(), amountFen, openid);
        Map<?, ?> resp = restClient.post()
                .uri(BASE_URL + JSAPI_PATH)
                .header("Authorization", authHeader("POST", JSAPI_PATH, body))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);
        Object prepayId = resp == null ? null : resp.get("prepay_id");
        if (prepayId == null) {
            throw BizException.badRequest("微信支付下单失败：" + (resp == null ? "无响应" : resp));
        }
        return String.valueOf(prepayId);
    }

    /**
     * 解密微信支付 V3 回调通知的 resource（AES-256-GCM），返回明文 JSON。
     */
    public String decryptNotify(Map<?, ?> resource) {
        requireConfigured();
        String ciphertext = (String) resource.get("ciphertext");
        String nonce = (String) resource.get("nonce");
        String associatedData = (String) resource.get("associated_data");
        try {
            byte[] key = properties.getApiV3Key().getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            if (associatedData != null && !associatedData.isBlank()) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw BizException.badRequest("微信回调解密失败");
        }
    }

    /**
     * 主动查单：按 out_trade_no 查询微信侧订单状态。
     */
    public Map<?, ?> queryOrder(String outTradeNo) {
        requireConfigured();
        String path = "/v3/pay/transactions/out-trade-no/" + outTradeNo
                + "?mchid=" + properties.getMchId();
        Map<?, ?> resp = restClient.get()
                .uri(BASE_URL + path)
                .header("Authorization", authHeader("GET", path, ""))
                .header("Accept", "application/json")
                .retrieve()
                .body(Map.class);
        return resp == null ? Map.of() : resp;
    }

    /**
     * 通用 RSA-SHA256 签名（商户私钥），返回 Base64。
     * 用于 JSAPI 调起支付二次签名等场景（message 格式由调用方按微信规范拼接）。
     */
    public String signMessage(String message) {
        return sign(message);
    }

    // ==================== 内部：签名 ====================

    private String authHeader(String method, String canonicalUrl, String body) {
        long timestamp = Instant.now().getEpochSecond();
        String nonce = randomNonce();
        String message = method + "\n" + canonicalUrl + "\n" + timestamp + "\n"
                + nonce + "\n" + body + "\n";
        String signature = sign(message);
        return SCHEME + " mchid=\"" + properties.getMchId() + "\",nonce_str=\"" + nonce
                + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + properties.getMchSerialNo()
                + "\",signature=\"" + signature + "\"";
    }

    private String sign(String message) {
        try {
            byte[] der = parsePem(Files.readAllBytes(Path.of(properties.getPrivateKeyPath())));
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw BizException.badRequest("微信支付签名失败：" + e.getMessage());
        }
    }

    /** 提取 PEM 中 base64 部分（兼容 PKCS1/PKCS8） */
    private byte[] parsePem(byte[] pemBytes) {
        String pem = new String(pemBytes, StandardCharsets.UTF_8)
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(pem);
    }

    private String randomNonce() {
        byte[] buf = new byte[16];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String appid() {
        return wechatProperties.getAppid();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw BizException.badRequest("微信支付未配置（开发环境请使用 mock-pay 直充）");
        }
    }
}
