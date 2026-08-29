package com.example.leaseplatform.usr.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.usr.dto.TokenVO;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import com.example.leaseplatform.usr.service.WechatService.WechatSession;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试（登录 / 刷新 / 登出 + Redis 会话）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsrUserMapper userMapper;
    @Mock
    private WechatService wechatService;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userMapper, wechatService, tokenProvider, redisTemplate);
        // 部分用例不触及 Redis，故 lenient
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private UsrUser activeUser(Long id) {
        UsrUser u = new UsrUser();
        u.setId(id);
        u.setUserType(3);
        u.setStatus(1);
        return u;
    }

    private Claims refreshClaims(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get(eq(JwtTokenProvider.CLAIM_TOKEN_TYPE), eq(String.class)))
                .thenReturn(JwtTokenProvider.TYPE_REFRESH);
        return claims;
    }

    @Test
    void login_newUser_shouldCreateAndIssueTokens() {
        when(wechatService.code2session("abc")).thenReturn(new WechatSession("wx_abc", null));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userMapper.insert(any(UsrUser.class))).thenAnswer(inv -> {
            UsrUser u = inv.getArgument(0);
            u.setId(1L);
            return 1;
        });
        when(tokenProvider.createAccessToken(1L, 3)).thenReturn("at");
        when(tokenProvider.createRefreshToken(1L)).thenReturn("rt");
        when(tokenProvider.getAccessTokenExpireSeconds()).thenReturn(7200L);
        when(tokenProvider.getRefreshTokenExpireSeconds()).thenReturn(604800L);

        TokenVO vo = service.login("abc");

        assertThat(vo.getAccessToken()).isEqualTo("at");
        assertThat(vo.getRefreshToken()).isEqualTo("rt");
        assertThat(vo.getTokenType()).isEqualTo("Bearer");
        assertThat(vo.getExpiresIn()).isEqualTo(7200);

        ArgumentCaptor<UsrUser> captor = ArgumentCaptor.forClass(UsrUser.class);
        verify(userMapper).insert(captor.capture());
        UsrUser created = captor.getValue();
        assertThat(created.getOpenid()).isEqualTo("wx_abc");
        assertThat(created.getNickname()).isEqualTo("微信用户");
        assertThat(created.getUserType()).isEqualTo(3);
        assertThat(created.getStatus()).isEqualTo(1);
        assertThat(created.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(valueOps).set("auth:refresh:1", "rt", Duration.ofSeconds(604800));
        verify(userMapper).updateById(any(UsrUser.class));
    }

    @Test
    void login_existingUser_shouldNotDuplicate() {
        when(wechatService.code2session("abc")).thenReturn(new WechatSession("wx_abc", null));
        UsrUser existing = activeUser(1L);
        existing.setOpenid("wx_abc");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(tokenProvider.createAccessToken(1L, 3)).thenReturn("at");
        when(tokenProvider.createRefreshToken(1L)).thenReturn("rt");
        when(tokenProvider.getAccessTokenExpireSeconds()).thenReturn(7200L);
        when(tokenProvider.getRefreshTokenExpireSeconds()).thenReturn(604800L);

        service.login("abc");

        verify(userMapper, never()).insert(any(UsrUser.class));
        verify(userMapper).updateById(existing);
    }

    @Test
    void login_disabledUser_shouldThrow401() {
        when(wechatService.code2session("abc")).thenReturn(new WechatSession("wx_abc", null));
        UsrUser disabled = activeUser(1L);
        disabled.setStatus(0);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(disabled);

        assertThatThrownBy(() -> service.login("abc"))
                .isInstanceOf(BizException.class)
                .hasMessage("账号已被禁用");
        verify(userMapper, never()).updateById(any(UsrUser.class));
    }

    @Test
    void refresh_valid_shouldRotateTokens() {
        Claims claims = refreshClaims(1L);
        when(tokenProvider.parse("rt")).thenReturn(claims);
        when(valueOps.get("auth:refresh:1")).thenReturn("rt");
        when(userMapper.selectById(1L)).thenReturn(activeUser(1L));
        when(tokenProvider.createAccessToken(1L, 3)).thenReturn("at2");
        when(tokenProvider.createRefreshToken(1L)).thenReturn("rt2");
        when(tokenProvider.getAccessTokenExpireSeconds()).thenReturn(7200L);
        when(tokenProvider.getRefreshTokenExpireSeconds()).thenReturn(604800L);

        TokenVO vo = service.refresh("rt");

        assertThat(vo.getAccessToken()).isEqualTo("at2");
        verify(redisTemplate).delete("auth:refresh:1");
        verify(valueOps).set("auth:refresh:1", "rt2", Duration.ofSeconds(604800));
    }

    @Test
    void refresh_staleToken_shouldThrow401() {
        Claims claims = refreshClaims(1L);
        when(tokenProvider.parse("rt")).thenReturn(claims);
        when(valueOps.get("auth:refresh:1")).thenReturn("other");

        assertThatThrownBy(() -> service.refresh("rt"))
                .isInstanceOf(BizException.class)
                .hasMessage("刷新令牌已失效，请重新登录");
        verify(redisTemplate, never()).delete("auth:refresh:1");
    }

    @Test
    void refresh_invalidSignature_shouldThrow401() {
        when(tokenProvider.parse("bad")).thenThrow(new SignatureException("bad signature"));

        assertThatThrownBy(() -> service.refresh("bad"))
                .isInstanceOf(BizException.class)
                .hasMessage("刷新令牌无效或已过期");
    }

    @Test
    void refresh_notRefreshType_shouldThrow401() {
        Claims claims = mock(Claims.class);
        when(claims.get(eq(JwtTokenProvider.CLAIM_TOKEN_TYPE), eq(String.class)))
                .thenReturn(JwtTokenProvider.TYPE_ACCESS);
        when(tokenProvider.parse("at")).thenReturn(claims);

        assertThatThrownBy(() -> service.refresh("at"))
                .isInstanceOf(BizException.class)
                .hasMessage("刷新令牌无效或已过期");
    }

    @Test
    void logout_matching_shouldDeleteSession() {
        Claims claims = refreshClaims(1L);
        when(tokenProvider.parse("rt")).thenReturn(claims);
        when(valueOps.get("auth:refresh:1")).thenReturn("rt");

        service.logout("rt");

        verify(redisTemplate).delete("auth:refresh:1");
    }

    @Test
    void logout_staleToken_shouldNotDelete() {
        Claims claims = refreshClaims(1L);
        when(tokenProvider.parse("rt")).thenReturn(claims);
        when(valueOps.get("auth:refresh:1")).thenReturn("other");

        service.logout("rt");

        verify(redisTemplate, never()).delete("auth:refresh:1");
    }

    @Test
    void logout_invalidToken_shouldBeIdempotent() {
        when(tokenProvider.parse("bad")).thenThrow(new JwtException("invalid") {
        });

        service.logout("bad");

        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void logout_blankToken_shouldDoNothing() {
        service.logout("  ");

        verify(redisTemplate, never()).delete(any(String.class));
    }
}
