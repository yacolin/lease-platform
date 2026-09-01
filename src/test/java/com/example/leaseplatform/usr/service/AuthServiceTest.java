package com.example.leaseplatform.usr.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.usr.dto.TokenVO;
import com.example.leaseplatform.usr.entity.UsrAdmin;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrAdminMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;

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
 * 认证服务单元测试（管理员登录 / 微信登录 / 刷新 / 登出 + Redis 会话）。
 * Redis key：auth:refresh:{userType}:{userId}（管理员与微信用户 id 空间隔离）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RT_WX = "auth:refresh:3:1";
    private static final String RT_ADMIN = "auth:refresh:1:1";

    @Mock
    private UsrUserMapper userMapper;
    @Mock
    private UsrAdminMapper adminMapper;
    @Mock
    private WechatService wechatService;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userMapper, adminMapper, wechatService, tokenProvider, redisTemplate, passwordEncoder);
        // 部分用例不触及 Redis，故 lenient
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private UsrUser activeWxUser(Long id) {
        UsrUser u = new UsrUser();
        u.setId(id);
        u.setUserType(3);
        u.setStatus(1);
        return u;
    }

    private UsrAdmin activeAdmin(Long id) {
        UsrAdmin a = new UsrAdmin();
        a.setId(id);
        a.setUsername("admin");
        a.setPasswordHash("hash");
        a.setStatus(1);
        return a;
    }

    private Claims refreshClaims(Long userId, Integer userType) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get(eq(JwtTokenProvider.CLAIM_TOKEN_TYPE), eq(String.class)))
                .thenReturn(JwtTokenProvider.TYPE_REFRESH);
        when(claims.get(eq(JwtTokenProvider.CLAIM_USER_TYPE), eq(Integer.class)))
                .thenReturn(userType);
        return claims;
    }

    // ==================== 管理端登录 ====================

    @Test
    void adminLogin_valid_shouldIssueAdminTokens() {
        when(adminMapper.selectOne(any(Wrapper.class))).thenReturn(activeAdmin(1L));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(tokenProvider.createAccessToken(1L, 1)).thenReturn("at");
        when(tokenProvider.createRefreshToken(1L, 1)).thenReturn("rt");
        when(tokenProvider.getAccessTokenExpireSeconds()).thenReturn(7200L);
        when(tokenProvider.getRefreshTokenExpireSeconds()).thenReturn(604800L);

        TokenVO vo = service.adminLogin("admin", "123456");

        assertThat(vo.getAccessToken()).isEqualTo("at");
        verify(valueOps).set(RT_ADMIN, "rt", Duration.ofSeconds(604800));
        verify(adminMapper).updateById(any(UsrAdmin.class));
    }

    @Test
    void adminLogin_wrongPassword_shouldThrow401() {
        when(adminMapper.selectOne(any(Wrapper.class))).thenReturn(activeAdmin(1L));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.adminLogin("admin", "wrong"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名或密码错误");
        verify(adminMapper, never()).updateById(any(UsrAdmin.class));
    }

    @Test
    void adminLogin_unknownUser_shouldThrow401() {
        when(adminMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.adminLogin("nobody", "123456"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void adminLogin_disabled_shouldThrow401() {
        UsrAdmin disabled = activeAdmin(1L);
        disabled.setStatus(0);
        when(adminMapper.selectOne(any(Wrapper.class))).thenReturn(disabled);

        assertThatThrownBy(() -> service.adminLogin("admin", "123456"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名或密码错误");
    }

    // ==================== 微信登录 ====================

    @Test
    void wxLogin_newUser_shouldCreateAndIssueTokens() {
        when(wechatService.code2session("abc")).thenReturn(new WechatSession("mock_dev_user", null));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userMapper.insert(any(UsrUser.class))).thenAnswer(inv -> {
            UsrUser u = inv.getArgument(0);
            u.setId(1L);
            return 1;
        });
        when(tokenProvider.createAccessToken(1L, 3)).thenReturn("at");
        when(tokenProvider.createRefreshToken(1L, 3)).thenReturn("rt");
        when(tokenProvider.getAccessTokenExpireSeconds()).thenReturn(7200L);
        when(tokenProvider.getRefreshTokenExpireSeconds()).thenReturn(604800L);

        TokenVO vo = service.wxLogin("abc");

        assertThat(vo.getAccessToken()).isEqualTo("at");
        assertThat(vo.getRefreshToken()).isEqualTo("rt");
        assertThat(vo.getTokenType()).isEqualTo("Bearer");
        assertThat(vo.getExpiresIn()).isEqualTo(7200);

        ArgumentCaptor<UsrUser> captor = ArgumentCaptor.forClass(UsrUser.class);
        verify(userMapper).insert(captor.capture());
        UsrUser created = captor.getValue();
        assertThat(created.getOpenid()).isEqualTo("mock_dev_user");
        assertThat(created.getNickname()).isEqualTo("微信用户");
        assertThat(created.getUserType()).isEqualTo(3);
        assertThat(created.getStatus()).isEqualTo(1);
        // 1.5：余额不再存在于用户表，账户由 AccountService 懒创建（0 余额）

        verify(valueOps).set(RT_WX, "rt", Duration.ofSeconds(604800));
        verify(userMapper).updateById(any(UsrUser.class));
    }

    @Test
    void wxLogin_existingUser_shouldNotDuplicate() {
        when(wechatService.code2session("abc")).thenReturn(new WechatSession("mock_dev_user", null));
        UsrUser existing = activeWxUser(1L);
        existing.setOpenid("mock_dev_user");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(tokenProvider.createAccessToken(1L, 3)).thenReturn("at");
        when(tokenProvider.createRefreshToken(1L, 3)).thenReturn("rt");
        when(tokenProvider.getAccessTokenExpireSeconds()).thenReturn(7200L);
        when(tokenProvider.getRefreshTokenExpireSeconds()).thenReturn(604800L);

        service.wxLogin("abc");

        verify(userMapper, never()).insert(any(UsrUser.class));
        verify(userMapper).updateById(existing);
    }

    @Test
    void wxLogin_disabledUser_shouldThrow401() {
        when(wechatService.code2session("abc")).thenReturn(new WechatSession("mock_dev_user", null));
        UsrUser disabled = activeWxUser(1L);
        disabled.setStatus(0);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(disabled);

        assertThatThrownBy(() -> service.wxLogin("abc"))
                .isInstanceOf(BizException.class)
                .hasMessage("账号已被禁用");
        verify(userMapper, never()).updateById(any(UsrUser.class));
    }

    // ==================== 刷新 / 登出 ====================

    @Test
    void refresh_wxUser_shouldRotateTokens() {
        Claims claims = refreshClaims(1L, 3);
        when(tokenProvider.parse("rt")).thenReturn(claims);
        when(valueOps.get(RT_WX)).thenReturn("rt");
        when(userMapper.selectById(1L)).thenReturn(activeWxUser(1L));
        when(tokenProvider.createAccessToken(1L, 3)).thenReturn("at2");
        when(tokenProvider.createRefreshToken(1L, 3)).thenReturn("rt2");
        when(tokenProvider.getAccessTokenExpireSeconds()).thenReturn(7200L);
        when(tokenProvider.getRefreshTokenExpireSeconds()).thenReturn(604800L);

        TokenVO vo = service.refresh("rt");

        assertThat(vo.getAccessToken()).isEqualTo("at2");
        verify(redisTemplate).delete(RT_WX);
        verify(valueOps).set(RT_WX, "rt2", Duration.ofSeconds(604800));
    }

    @Test
    void refresh_admin_shouldRotateAndCheckAdminTable() {
        Claims claims = refreshClaims(1L, 1);
        when(tokenProvider.parse("rt")).thenReturn(claims);
        when(valueOps.get(RT_ADMIN)).thenReturn("rt");
        when(adminMapper.selectById(1L)).thenReturn(activeAdmin(1L));
        when(tokenProvider.createAccessToken(1L, 1)).thenReturn("at2");
        when(tokenProvider.createRefreshToken(1L, 1)).thenReturn("rt2");
        when(tokenProvider.getAccessTokenExpireSeconds()).thenReturn(7200L);
        when(tokenProvider.getRefreshTokenExpireSeconds()).thenReturn(604800L);

        TokenVO vo = service.refresh("rt");

        assertThat(vo.getAccessToken()).isEqualTo("at2");
        verify(adminMapper).selectById(1L);
        verify(redisTemplate).delete(RT_ADMIN);
    }

    @Test
    void refresh_staleToken_shouldThrow401() {
        Claims claims = refreshClaims(1L, 3);
        when(tokenProvider.parse("rt")).thenReturn(claims);
        when(valueOps.get(RT_WX)).thenReturn("other");

        assertThatThrownBy(() -> service.refresh("rt"))
                .isInstanceOf(BizException.class)
                .hasMessage("刷新令牌已失效，请重新登录");
        verify(redisTemplate, never()).delete(RT_WX);
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
    void refresh_userDisabled_shouldThrow401() {
        Claims claims = refreshClaims(1L, 3);
        when(tokenProvider.parse("rt")).thenReturn(claims);
        when(valueOps.get(RT_WX)).thenReturn("rt");
        // 用户被禁用
        UsrUser disabled = activeWxUser(1L);
        disabled.setStatus(0);
        when(userMapper.selectById(1L)).thenReturn(disabled);

        assertThatThrownBy(() -> service.refresh("rt"))
                .isInstanceOf(BizException.class)
                .hasMessage("账号不存在或已被禁用");
        verify(redisTemplate, never()).delete(RT_WX);
    }

    @Test
    void logout_matching_shouldDeleteSession() {
        Claims claims = refreshClaims(1L, 3);
        when(tokenProvider.parse("rt")).thenReturn(claims);
        when(valueOps.get(RT_WX)).thenReturn("rt");

        service.logout("rt");

        verify(redisTemplate).delete(RT_WX);
    }

    @Test
    void logout_staleToken_shouldNotDelete() {
        Claims claims = refreshClaims(1L, 3);
        when(tokenProvider.parse("rt")).thenReturn(claims);
        when(valueOps.get(RT_WX)).thenReturn("other");

        service.logout("rt");

        verify(redisTemplate, never()).delete(RT_WX);
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
