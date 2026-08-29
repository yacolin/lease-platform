package com.example.leaseplatform.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 当前登录用户主体（SecurityContext 的 principal）。
 * 字段来自 JWT 声明（userId / userType），由 JwtAuthenticationFilter 写入；
 * 业务代码通过 {@link UserContext} 取用。
 */
@Getter
public class LoginUser {

    private final Long id;
    private final Integer userType;
    private final Collection<? extends GrantedAuthority> authorities;

    private LoginUser(Long id, Integer userType, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.userType = userType;
        this.authorities = authorities;
    }

    public static LoginUser of(Long userId, Integer userType) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (userType != null && userType == 1) {
            // 超级管理员（usr_users.user_type=1）
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return new LoginUser(userId, userType, authorities);
    }

    public boolean isAdmin() {
        return userType != null && userType == 1;
    }
}
