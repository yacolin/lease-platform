package com.example.leaseplatform.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 配置骨架：
 * - 白名单路径放行（lease.security.whitelist，如登录/注册、商品/菜单/会议室浏览）
 * - 全局 CORS（lease.security.cors）
 * - 无状态 API：关闭 CSRF / 表单登录 / httpBasic
 * - 其余请求暂要求认证；JWT 认证过滤器接入后按 token 校验（见 jwt.* 配置）
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CORS 预检请求直接放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 白名单路径无需认证
                        .requestMatchers(securityProperties.getWhitelist().toArray(new String[0])).permitAll()
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
