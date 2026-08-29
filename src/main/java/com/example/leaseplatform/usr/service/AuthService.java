package com.example.leaseplatform.usr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 认证服务（双登录设计）：
 * - 后台管理员登录 {@link #adminLogin}：/api/v1/auth/login，username + password（bcrypt），签发 user_type=1 的 token；
 * - 微信小程序登录 {@link #wxLogin}：/api/v1/auth/wx-login，code2session → 查/建 usr_users，签发 user_type=2/3 的 token；
 * - 令牌刷新 / 登出：refresh token 存 Redis（key auth:refresh:{userType}:{userId}），同一会话仅保留一个有效刷新令牌。
 * <p>
 * 身份以 (userType, userId) 复合：usr_admins 与 usr_users 自增 id 各自从 1 开始，
 * 刷新 / 登出时按 refresh token 中的 userType 定位对应表与 Redis key，避免跨类型串号。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

    /** 后台管理员用户类型（usr_admins → user_type=1，token 带 ROLE_ADMIN） */
    public static final int USER_TYPE_ADMIN = 1;

    private final UsrUserMapper userMapper;
    private final UsrAdminMapper adminMapper;
    private final WechatService wechatService;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;

    /**
     * 后台管理员登录：校验用户名/密码（bcrypt）→ 签发 access + refresh token（user_type=1）。
     * 账号不存在 / 密码错误 / 账号禁用统一返回"用户名或密码错误"（不泄露账号状态）。
     */
    @Transactional
    public TokenVO adminLogin(String username, String password) {
        UsrAdmin admin = adminMapper.selectOne(new LambdaQueryWrapper<UsrAdmin>()
                .eq(UsrAdmin::getUsername, username));
        if (admin == null || admin.getStatus() == null || admin.getStatus() != 1
                || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        admin.setLastLoginAt(LocalDateTime.now());
        adminMapper.updateById(admin);
        return issueTokens(admin.getId(), USER_TYPE_ADMIN);
    }

    /**
     * 微信小程序登录：code2session → 查/建 usr_users → 签发 access + refresh token。
     */
    @Transactional
    public TokenVO wxLogin(String code) {
        WechatSession session = wechatService.code2session(code);
        UsrUser user = userMapper.selectOne(new LambdaQueryWrapper<UsrUser>()
                .eq(UsrUser::getOpenid, session.openid()));
        if (user == null) {
            user = createUser(session);
        } else if (user.getStatus() != null && user.getStatus() == 0) {
            throw BizException.unauthorized("账号已被禁用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return issueTokens(user.getId(), user.getUserType());
    }

    /**
     * 刷新令牌：校验 refresh token（签名/过期 + Redis 会话匹配 + 用户仍有效）
     * → 轮换签发新令牌对。
     */
    @Transactional
    public TokenVO refresh(String refreshToken) {
        RefreshIdentity identity = resolveRefreshIdentity(refreshToken);
        String key = refreshKey(identity.userType(), identity.userId());
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equals(refreshToken)) {
            throw BizException.unauthorized("刷新令牌已失效，请重新登录");
        }
        requireActive(identity.userId(), identity.userType());
        // 轮换：作废旧刷新令牌，签发新令牌对
        redisTemplate.delete(key);
        return issueTokens(identity.userId(), identity.userType());
    }

    /**
     * 登出：作废当前用户的刷新令牌（若携带的 refresh token 与 Redis 会话匹配）。
     * 幂等：令牌无效 / 已失效时静默成功。
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            RefreshIdentity identity = resolveRefreshIdentity(refreshToken);
            String key = refreshKey(identity.userType(), identity.userId());
            String stored = redisTemplate.opsForValue().get(key);
            if (refreshToken.equals(stored)) {
                redisTemplate.delete(key);
            }
        } catch (BizException ignored) {
            // 令牌无效：无需处理
        }
    }

    /** 校验并解析 refresh token，返回 {userType, userId}；失败抛 401 */
    private RefreshIdentity resolveRefreshIdentity(String refreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw BizException.unauthorized("刷新令牌无效或已过期");
        }
        if (!JwtTokenProvider.TYPE_REFRESH.equals(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
            throw BizException.unauthorized("刷新令牌无效或已过期");
        }
        Integer userType = claims.get(JwtTokenProvider.CLAIM_USER_TYPE, Integer.class);
        if (userType == null) {
            throw BizException.unauthorized("刷新令牌无效或已过期");
        }
        return new RefreshIdentity(userType, Long.valueOf(claims.getSubject()));
    }

    /** 刷新 / 登出时校验对应用户仍存在且启用 */
    private void requireActive(Long userId, Integer userType) {
        if (userType != null && userType == USER_TYPE_ADMIN) {
            UsrAdmin admin = adminMapper.selectById(userId);
            if (admin == null || admin.getStatus() == null || admin.getStatus() != 1) {
                throw BizException.unauthorized("账号不存在或已被禁用");
            }
            return;
        }
        UsrUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.unauthorized("账号不存在或已被禁用");
        }
    }

    private UsrUser createUser(WechatSession session) {
        UsrUser user = new UsrUser();
        user.setOpenid(session.openid());
        user.setUnionid(session.unionid());
        user.setNickname("微信用户");
        user.setUserType(3);          // 路人用户
        user.setMemberLevel(0);
        user.setIsEnterpriseAdmin(0);
        user.setBalance(BigDecimal.ZERO);
        user.setGiftBalance(BigDecimal.ZERO);
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    private TokenVO issueTokens(Long userId, Integer userType) {
        String accessToken = tokenProvider.createAccessToken(userId, userType);
        String refreshToken = tokenProvider.createRefreshToken(userId, userType);
        redisTemplate.opsForValue().set(
                refreshKey(userType, userId),
                refreshToken,
                Duration.ofSeconds(tokenProvider.getRefreshTokenExpireSeconds()));
        return TokenVO.of(accessToken, refreshToken, tokenProvider.getAccessTokenExpireSeconds());
    }

    /** Redis 会话 key：auth:refresh:{userType}:{userId}（复合身份，避免管理员/用户 id 串号） */
    private static String refreshKey(Integer userType, Long userId) {
        return REFRESH_KEY_PREFIX + userType + ":" + userId;
    }

    /** refresh token 解析出的复合身份 */
    private record RefreshIdentity(Integer userType, Long userId) {
    }
}
