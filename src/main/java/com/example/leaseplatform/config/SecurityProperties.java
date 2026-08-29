package com.example.leaseplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Security 配置项（application.yml 的 lease.security.*）：
 * 白名单路径 + CORS 跨域配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lease.security")
public class SecurityProperties {

    /** 无需认证即可访问的路径（Ant 风格，支持 /** 通配） */
    private List<String> whitelist = new ArrayList<>();

    /** 管理端路径：仅后台管理员（user_type=1，/api/v1/auth/login 登录）可访问（Ant 风格） */
    private List<String> adminPaths = new ArrayList<>();

    /** CORS 跨域配置 */
    private Cors cors = new Cors();

    @Data
    public static class Cors {
        /** 允许的来源（携带凭证时不能为 *，需显式列域名） */
        private List<String> allowedOrigins = List.of("*");

        /** 允许的请求方法 */
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

        /** 允许的请求头 */
        private List<String> allowedHeaders = List.of("*");

        /** 允许前端读取的响应头（如 Authorization） */
        private List<String> exposedHeaders = new ArrayList<>();

        /** 是否允许携带凭证（cookie / 认证头） */
        private boolean allowCredentials = false;

        /** 预检请求缓存时间（秒） */
        private long maxAge = 3600;
    }
}
