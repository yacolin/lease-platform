package com.example.leaseplatform.security;

import com.example.leaseplatform.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 令牌签发 / 解析单元测试（jjwt HS256）。
 */
class JwtTokenProviderTest {

    private static final String SECRET =
            "Iyh/J3RcrpgUbprqYJmbNC6YV5DXAV9q9AUsKuXn7HQ82fNXFakPWoCYWdZuDEbs";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(props(7200, 604800));
    }

    private JwtProperties props(long accessExpire, long refreshExpire) {
        JwtProperties p = new JwtProperties();
        p.setSecret(SECRET);
        p.setAccessTokenExpireSeconds(accessExpire);
        p.setRefreshTokenExpireSeconds(refreshExpire);
        p.setHeader("Authorization");
        p.setPrefix("Bearer ");
        return p;
    }

    @Test
    void accessToken_roundtrip_shouldCarryUserIdAndType() {
        String token = provider.createAccessToken(1L, 3);

        Claims claims = provider.parse(token);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .isEqualTo(JwtTokenProvider.TYPE_ACCESS);
        assertThat(claims.get(JwtTokenProvider.CLAIM_USER_TYPE, Integer.class)).isEqualTo(3);
    }

    @Test
    void refreshToken_roundtrip_shouldCarryJtiAndUserType() {
        String token = provider.createRefreshToken(42L, 1);

        Claims claims = provider.parse(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))
                .isEqualTo(JwtTokenProvider.TYPE_REFRESH);
        assertThat(claims.get(JwtTokenProvider.CLAIM_USER_TYPE, Integer.class)).isEqualTo(1);
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void expiredToken_shouldThrowExpiredJwtException() {
        JwtTokenProvider shortLived = new JwtTokenProvider(props(-3600, 604800));
        String token = shortLived.createAccessToken(1L, 3);

        assertThatThrownBy(() -> provider.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void wrongSecret_shouldThrowSecurityException() {
        // 不同密钥：签名校验失败（WeakKeyException / SignatureException 均为 SecurityException）
        String otherSecret = Base64.getEncoder()
                .encodeToString("0123456789abcdefghijklmnopqrstuv".getBytes(StandardCharsets.UTF_8));
        JwtProperties other = props(7200, 604800);
        other.setSecret(otherSecret);
        JwtTokenProvider otherProvider = new JwtTokenProvider(other);

        String token = provider.createAccessToken(1L, 3);

        assertThatThrownBy(() -> otherProvider.parse(token))
                .isInstanceOf(io.jsonwebtoken.security.SecurityException.class);
    }

    @Test
    void malformedToken_shouldThrowJwtException() {
        assertThatThrownBy(() -> provider.parse("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void getters_shouldReturnConfiguredValues() {
        assertThat(provider.getHeader()).isEqualTo("Authorization");
        assertThat(provider.getPrefix()).isEqualTo("Bearer ");
        assertThat(provider.getAccessTokenExpireSeconds()).isEqualTo(7200);
        assertThat(provider.getRefreshTokenExpireSeconds()).isEqualTo(604800);
    }
}
