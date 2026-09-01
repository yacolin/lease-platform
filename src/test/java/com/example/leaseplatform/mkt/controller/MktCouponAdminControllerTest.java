package com.example.leaseplatform.mkt.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.mkt.dto.CouponVO;
import com.example.leaseplatform.mkt.service.MktCouponService;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.security.LoginUser;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 优惠券管理（1.6，管理端）Web 层测试。
 */
@WebMvcTest(MktCouponAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class MktCouponAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MktCouponService couponService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUpAuth() {
        LoginUser admin = LoginUser.of(9L, 1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private CouponVO vo() {
        CouponVO vo = new CouponVO();
        vo.setId(1L);
        vo.setCouponName("满30减5");
        vo.setCouponType(1);
        vo.setDiscountAmount(500L);
        return vo;
    }

    @Test
    void create_shouldReturnVo() throws Exception {
        when(couponService.create(any())).thenReturn(vo());

        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"couponName":"满30减5","couponType":1,"discountAmount":500,
                                 "thresholdAmount":3000,"bizType":1}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.couponName").value("满30减5"));
    }

    @Test
    void create_missingType_shouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponName\":\"满30减5\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void page_shouldReturnPaged() throws Exception {
        when(couponService.page(eq(1), eq(10), eq(1), eq(1), eq("满")))
                .thenReturn(PageResult.of(1, List.of(vo())));

        mockMvc.perform(get("/api/v1/coupons")
                        .param("couponType", "1").param("status", "1").param("keyword", "满"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
