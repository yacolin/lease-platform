package com.example.leaseplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序登录配置（application.yml 的 wechat.*）。
 * 开发期可用 mock：mock-enabled=true 时 code2session 直接返回 mock openid，无需真实微信环境。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    /** 小程序 AppID */
    private String appid;

    /** 小程序 AppSecret */
    private String secret;

    /** 开发 mock：true 时跳过 code2session 真实调用，按 code 生成确定性 openid */
    private boolean mockEnabled = true;
}
