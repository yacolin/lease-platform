package com.example.leaseplatform.config;

import com.example.leaseplatform.security.JwtAuthenticationFilter;
import com.example.leaseplatform.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 配置：
 * - 白名单路径放行（lease.security.whitelist，如登录/注册、商品/菜单/会议室浏览）
 * - 管理端路径要求 ROLE_ADMIN（lease.security.admin-paths，user_type=1 的 token，
 *   即 /api/v1/auth/login 后台管理员登录签发的令牌）
 * - JWT 认证过滤器（Authorization: Bearer → 写入 SecurityContext，见 security 包）
 * - 全局 CORS（lease.security.cors）
 * - 无状态 API：关闭 CSRF / 表单登录 / httpBasic
 * - 其余请求要求认证；未携带有效 token 时按未认证处理（默认 403）
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final JwtTokenProvider jwtTokenProvider;

    /** 管理员密码编码器（bcrypt，与 usr_admins.password_hash 种子一致） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // JWT 认证：解析 Bearer token → 校验签名/过期 → 写入 SecurityContext
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // CORS 预检请求直接放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 白名单路径无需认证
                        .requestMatchers(securityProperties.getWhitelist().toArray(new String[0])).permitAll()
                        // 管理端路径：仅后台管理员（ROLE_ADMIN = user_type=1）
                        .requestMatchers(securityProperties.getAdminPaths().toArray(new String[0])).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * 全局 CORS 配置源：由 SecurityConfig 提供 bean，
     * Security 过滤器链的 .cors() 与 MVC 层共用。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        SecurityProperties.Cors cors = securityProperties.getCors();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.getAllowedOrigins());
        config.setAllowedMethods(cors.getAllowedMethods());
        config.setAllowedHeaders(cors.getAllowedHeaders());
        config.setExposedHeaders(cors.getExposedHeaders());
        config.setAllowCredentials(cors.isAllowCredentials());
        config.setMaxAge(cors.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
