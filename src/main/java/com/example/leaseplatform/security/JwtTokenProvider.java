package com.example.leaseplatform.security;

import com.example.leaseplatform.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌签发与解析（HMAC-SHA，jjwt 按密钥长度自动选择 HS256/HS384/HS512；
 * 当前配置密钥 384 bit → HS384）。
 * - access token：subject=userId，claim tokenType=access，携带 userType；
 * - refresh token：subject=userId，claim tokenType=refresh，带 jti（唯一 id）。
 * 签名密钥与过期时间来自 {@link JwtProperties}（application.yml 的 jwt.*）。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    /** 令牌类型 claim 名 */
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    /** 用户类型 claim 名（仅 access token 携带） */
    public static final String CLAIM_USER_TYPE = "userType";

    private final JwtProperties properties;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
    }

    /** 签发 access token（过期时间见 jwt.access-token-expire-seconds） */
    public String createAccessToken(Long userId, Integer userType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .claim(CLAIM_USER_TYPE, userType == null ? 3 : userType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getAccessTokenExpireSeconds())))
                .signWith(key())
                .compact();
    }

    /** 签发 refresh token（过期时间见 jwt.refresh-token-expire-seconds）。
     *  携带 userType：usr_admins 与 usr_users 自增 id 各自从 1 开始，刷新时必须按
     *  (userType, userId) 复合身份定位会话。 */
    public String createRefreshToken(Long userId, Integer userType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .claim(CLAIM_USER_TYPE, userType == null ? 3 : userType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getRefreshTokenExpireSeconds())))
                .signWith(key())
                .compact();
    }

    /**
     * 解析并校验令牌（签名 + 过期）。失败抛 {@link JwtException} 或其子类
     * （ExpiredJwtException / SignatureException / MalformedJwtException 等）。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getHeader() {
        return properties.getHeader();
    }

    public String getPrefix() {
        return properties.getPrefix();
    }

    public long getAccessTokenExpireSeconds() {
        return properties.getAccessTokenExpireSeconds();
    }

    public long getRefreshTokenExpireSeconds() {
        return properties.getRefreshTokenExpireSeconds();
    }
}
