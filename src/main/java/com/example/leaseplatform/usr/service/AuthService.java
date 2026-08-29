package com.example.leaseplatform.usr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.usr.dto.TokenVO;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import com.example.leaseplatform.usr.service.WechatService.WechatSession;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 认证服务：微信登录（查/建用户 + 签发令牌）、令牌刷新（refresh token 存 Redis）、登出。
 * Redis key：{@code auth:refresh:{userId}} → refreshToken，TTL = jwt.refresh-token-expire-seconds，
 * 同一用户同时仅保留一个有效刷新令牌（新登录 / 刷新会轮换）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

    private final UsrUserMapper userMapper;
    private final WechatService wechatService;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    /**
     * 微信登录：code2session → 查/建 usr_users → 签发 access + refresh token。
     */
    @Transactional
    public TokenVO login(String code) {
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
        return issueTokens(user);
    }

    /**
     * 刷新令牌：校验 refresh token（签名/过期 + Redis 会话匹配）→ 轮换签发新令牌对。
     */
    @Transactional
    public TokenVO refresh(String refreshToken) {
        Long userId = resolveRefreshUserId(refreshToken);
        String stored = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
        if (stored == null || !stored.equals(refreshToken)) {
            throw BizException.unauthorized("刷新令牌已失效，请重新登录");
        }
        UsrUser user = requireActive(userId);
        // 轮换：作废旧刷新令牌，签发新令牌对
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
        return issueTokens(user);
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
            Long userId = resolveRefreshUserId(refreshToken);
            String stored = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
            if (refreshToken.equals(stored)) {
                redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
            }
        } catch (BizException ignored) {
            // 令牌无效：无需处理
        }
    }

    /** 校验并解析 refresh token，返回其中的 userId；失败抛 401 */
    private Long resolveRefreshUserId(String refreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw BizException.unauthorized("刷新令牌无效或已过期");
        }
        if (!JwtTokenProvider.TYPE_REFRESH.equals(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
            throw BizException.unauthorized("刷新令牌无效或已过期");
        }
        return Long.valueOf(claims.getSubject());
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

    private UsrUser requireActive(Long userId) {
        UsrUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.unauthorized("账号不存在或已被禁用");
        }
        return user;
    }

    private TokenVO issueTokens(UsrUser user) {
        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getUserType());
        String refreshToken = tokenProvider.createRefreshToken(user.getId());
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getId(),
                refreshToken,
                Duration.ofSeconds(tokenProvider.getRefreshTokenExpireSeconds()));
        return TokenVO.of(accessToken, refreshToken, tokenProvider.getAccessTokenExpireSeconds());
    }
}
