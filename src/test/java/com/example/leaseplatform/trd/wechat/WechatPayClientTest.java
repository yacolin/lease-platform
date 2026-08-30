package com.example.leaseplatform.trd.wechat;

import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.config.WechatPayProperties;
import com.example.leaseplatform.config.WechatProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 微信支付客户端单元测试：RSA 签名 / 回调 AES-GCM 解密 / 未配置降级。
 */
class WechatPayClientTest {

    private static final String V3_KEY = "0123456789abcdef0123456789abcdef"; // 32 字节

    private WechatPayProperties payProps;
    private WechatProperties wechatProps;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        payProps = new WechatPayProperties();
        payProps.setMchId("1900000001");
        payProps.setApiV3Key(V3_KEY);
        payProps.setMchSerialNo("SERIAL1");
        payProps.setNotifyUrl("https://x/api/v1/wx/payments/notify");
        wechatProps = new WechatProperties();
        wechatProps.setAppid("wx-app-1");
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        // 写 PKCS8 私钥 PEM 到临时文件
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        Path keyFile = Files.createTempFile("wxpay-key", ".pem");
        Files.writeString(keyFile, pem);
        payProps.setPrivateKeyPath(keyFile.toString());
    }

    private WechatPayClient client() {
        return new WechatPayClient(payProps, wechatProps);
    }

    @Test
    void signMessage_shouldBeVerifiableWithPublicKey() throws Exception {
        String message = "POST\n/v3/pay/transactions/jsapi\n123\nnonce\n{}\n";
        String signature = client().signMessage(message);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(message.getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getDecoder().decode(signature))).isTrue();
    }

    @Test
    void decryptNotify_shouldDecryptAesGcmResource() throws Exception {
        // 用同一 API v3 key 加密一段 JSON（模拟微信回调 resource）
        String plain = "{\"out_trade_no\":\"RC123\",\"trade_state\":\"SUCCESS\"}";
        byte[] key = V3_KEY.getBytes(StandardCharsets.UTF_8);
        byte[] nonce = "abcdefghijklmnop".getBytes(StandardCharsets.UTF_8);
        String associatedData = "transaction";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        String ciphertext = Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));

        String decrypted = client().decryptNotify(Map.of(
                "ciphertext", ciphertext, "nonce", "abcdefghijklmnop",
                "associated_data", associatedData));

        assertThat(decrypted).contains("RC123");
    }

    @Test
    void unconfigured_shouldThrow() {
        WechatPayProperties empty = new WechatPayProperties();
        WechatPayClient unconfigured = new WechatPayClient(empty, new WechatProperties());

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.createJsapiOrder("o", "RC1", 100, "x"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("微信支付未配置");
        assertThatThrownBy(() -> unconfigured.decryptNotify(Map.of()))
                .isInstanceOf(BizException.class);
    }
}
