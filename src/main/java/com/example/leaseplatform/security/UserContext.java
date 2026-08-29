package com.example.leaseplatform.security;

import com.example.leaseplatform.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具：从 SecurityContext 取 principal。
 * 未登录（无 token / 令牌无效）时抛 401 {@link BizException}。
 */
public final class UserContext {

    private UserContext() {
    }

    /** 当前登录用户主体，未登录抛 401 */
    public static LoginUser getLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw BizException.unauthorized("未登录或登录已过期");
    }

    /** 当前登录用户 ID */
    public static Long getUserId() {
        return getLoginUser().getId();
    }

    /** 当前登录用户类型（usr_users.user_type） */
    public static Integer getUserType() {
        return getLoginUser().getUserType();
    }
}
