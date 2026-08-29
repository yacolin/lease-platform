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

    /** 开发 mock：true 时跳过 code2session 真实调用，固定复用 {@link #mockOpenid} */
    private boolean mockEnabled = true;

    /** 开发模式固定 openid（未配置微信时使用；wx.login() 的 code 一次性随机，
     *  不能拿它拼 openid，否则每次登录都会注册新用户） */
    private String mockOpenid = "mock_dev_user";
}
