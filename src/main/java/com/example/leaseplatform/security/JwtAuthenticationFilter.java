package com.example.leaseplatform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器：
 * 解析请求头 {@code Authorization: Bearer <token>} → 校验签名/过期（access token）→
 * 将 {@link LoginUser} 写入 SecurityContext；缺失 / 无效 / 过期令牌一律保持匿名，
 * 由 Security 链对受保护路径返回 403（未认证）。
 * <p>
 * 不查询数据库：直接信任令牌声明（userId / userType），与 /api/v1/me 等服务层
 * 的库表校验配合，满足"校验签名/过期 → 写入 SecurityContext"的 P0 目标。
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(tokenProvider.getHeader());
        if (header != null && header.startsWith(tokenProvider.getPrefix())) {
            String token = header.substring(tokenProvider.getPrefix().length());
            try {
                Claims claims = tokenProvider.parse(token);
                if (JwtTokenProvider.TYPE_ACCESS.equals(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
                    Long userId = Long.valueOf(claims.getSubject());
                    Integer userType = claims.get(JwtTokenProvider.CLAIM_USER_TYPE, Integer.class);
                    LoginUser loginUser = LoginUser.of(userId, userType);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // 无效 / 过期 / 伪造令牌：保持匿名，由授权规则统一返回 403
            }
        }
        filterChain.doFilter(request, response);
    }
}
