package com.example.leaseplatform.usr.controller;

import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.security.LoginUser;
import com.example.leaseplatform.usr.dto.MeVO;
import com.example.leaseplatform.usr.service.UsrUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 我的接口 Web 层测试（/api/v1/me 需登录）。
 * 说明：@WebMvcTest 切片不装配真实 Security 过滤器链（未认证/无效 token 的 403
 * 行为由 UsrAuthIntegrationTest 全量上下文覆盖）；此处预置 SecurityContext 模拟已登录，
 * 仅验证控制器映射与参数校验。
 */
@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsrUserService userService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUpAuth() {
        LoginUser loginUser = LoginUser.of(1L, 3);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private MeVO meVO() {
        MeVO vo = new MeVO();
        vo.setId(1L);
        vo.setNickname("微信用户");
        vo.setUserType(3);
        vo.setMemberLevel(0);
        vo.setBalance(1250L);
        vo.setGiftBalance(500L);
        vo.setStatus(1);
        return vo;
    }

    @Test
    void me_shouldReturnProfile() throws Exception {
        when(userService.me()).thenReturn(meVO());

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("微信用户"))
                .andExpect(jsonPath("$.data.balance").value(1250))
                .andExpect(jsonPath("$.data.memberLevel").value(0));
    }

    @Test
    void updateMe_shouldReturnProfile() throws Exception {
        when(userService.updateMe(any())).thenReturn(meVO());

        mockMvc.perform(put("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"小明\",\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("微信用户"));
    }

    @Test
    void updateMe_invalidPhone_shouldReturn422() throws Exception {
        mockMvc.perform(put("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"12345\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
