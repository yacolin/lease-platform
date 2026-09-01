package com.example.leaseplatform.mkt.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.mkt.dto.UserCouponVO;
import com.example.leaseplatform.mkt.service.MktUserCouponService;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 我的优惠券（1.6，小程序端）Web 层测试。
 */
@WebMvcTest(MeCouponController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class MeCouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MktUserCouponService userCouponService;
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

    @Test
    void claim_shouldReturnVo() throws Exception {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(10L);
        vo.setCouponName("满30减5");
        when(userCouponService.claim(eq(1L), eq(1L))).thenReturn(vo);

        mockMvc.perform(post("/api/v1/me/coupons/1/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.couponName").value("满30减5"));
    }

    @Test
    void myCoupons_shouldReturnPaged() throws Exception {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(10L);
        vo.setStatus(0);
        when(userCouponService.myCoupons(eq(1L), eq(1), eq(10), eq(0)))
                .thenReturn(PageResult.of(1, List.of(vo)));

        mockMvc.perform(get("/api/v1/me/coupons").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
