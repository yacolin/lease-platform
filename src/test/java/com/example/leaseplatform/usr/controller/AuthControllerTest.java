package com.example.leaseplatform.usr.controller;

import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.usr.dto.TokenVO;
import com.example.leaseplatform.usr.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口 Web 层测试（/api/v1/auth/** 在白名单内，无需认证）。
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private TokenVO tokenVO() {
        return TokenVO.of("at", "rt", 7200);
    }

    @Test
    void login_shouldReturnTokenVO() throws Exception {
        when(authService.login("abc")).thenReturn(tokenVO());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("at"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200));
    }

    @Test
    void login_missingCode_shouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void refresh_shouldReturnTokenVO() throws Exception {
        when(authService.refresh("rt")).thenReturn(tokenVO());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"rt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("at"));
    }

    @Test
    void logout_shouldSucceed() throws Exception {
        doNothing().when(authService).logout("rt");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"rt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void logout_withoutBody_shouldSucceed() throws Exception {
        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
