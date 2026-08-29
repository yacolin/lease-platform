package com.example.leaseplatform.usr.service;

import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.config.WechatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 微信登录服务：code2session。
 * - 开发 mock（wechat.mock-enabled=true）：固定复用 wechat.mock-openid（默认 mock_dev_user），
 *   无需真实微信环境，前后端可直接联调；注意 wx.login() 的 code 是一次性随机值、
 *   每次调用都不同，不能拿它拼 openid（否则每次登录都会注册新用户）；
 * - 生产：调用微信 jscode2session 换取 openid / unionid。
 */
@Service
@RequiredArgsConstructor
public class WechatService {

    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final WechatProperties properties;
    private final RestClient restClient = RestClient.create();

    /** code2session 结果 */
    public record WechatSession(String openid, String unionid) {
    }

    public WechatSession code2session(String code) {
        if (properties.isMockEnabled()) {
            return new WechatSession(properties.getMockOpenid(), null);
        }
        Map<?, ?> resp = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https").host("api.weixin.qq.com").path("/sns/jscode2session")
                        .queryParam("appid", properties.getAppid())
                        .queryParam("secret", properties.getSecret())
                        .queryParam("js_code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(Map.class);
        if (resp == null) {
            throw BizException.badRequest("微信登录失败：无响应");
        }
        Number errcode = (Number) resp.get("errcode");
        if (errcode != null && errcode.intValue() != 0) {
            throw BizException.badRequest("微信登录失败：" + resp.get("errmsg"));
        }
        String openid = (String) resp.get("openid");
        if (openid == null || openid.isBlank()) {
            throw BizException.badRequest("微信登录失败：未获取到 openid");
        }
        return new WechatSession(openid, (String) resp.get("unionid"));
    }
}
