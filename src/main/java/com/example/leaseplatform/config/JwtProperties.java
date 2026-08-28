package com.example.leaseplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项（application.yml 的 jwt.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥：Base64 编码，HS256 要求 >= 256 bit */
    private String secret;

    /** Access Token 过期时间（秒） */
    private long accessTokenExpireSeconds;

    /** Refresh Token 过期时间（秒） */
    private long refreshTokenExpireSeconds;

    /** 携带 token 的请求头名称 */
    private String header = "Authorization";

    /** 请求头值前缀 */
    private String prefix = "Bearer ";
}
