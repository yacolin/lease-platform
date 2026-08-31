package com.example.leaseplatform.trd.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.ord.dto.OrderStatusHistoryVO;
import com.example.leaseplatform.ord.service.OrderStatusHistoryService;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.security.LoginUser;
import com.example.leaseplatform.trd.dto.PaymentVO;
import com.example.leaseplatform.trd.dto.RefundVO;
import com.example.leaseplatform.trd.service.PaymentService;
import com.example.leaseplatform.trd.service.RefundService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 交易管理（1.2，管理端）Web 层测试：支付单 / 退款单 / 状态历史。
 */
@WebMvcTest(TradeAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class TradeAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private RefundService refundService;
    @MockitoBean
    private OrderStatusHistoryService statusHistoryService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    /** 管理员登录态（user_type=1 → ROLE_ADMIN） */
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

    @Test
    void payments_shouldReturnPaged() throws Exception {
        PaymentVO vo = new PaymentVO();
        vo.setId(800L);
        vo.setPaymentNo("PAY123");
        vo.setStatus(1);
        when(paymentService.adminPage(eq(1), eq(10), eq("PAY"), eq(2), eq(1)))
                .thenReturn(PageResult.of(1, List.of(vo)));

        mockMvc.perform(get("/api/v1/payments")
                        .param("paymentNo", "PAY").param("bizType", "2").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void refunds_shouldReturnPaged() throws Exception {
        RefundVO vo = new RefundVO();
        vo.setId(700L);
        vo.setRefundNo("RF123");
        when(refundService.adminPage(eq(1), eq(10), eq("RF"), eq(1)))
                .thenReturn(PageResult.of(1, List.of(vo)));

        mockMvc.perform(get("/api/v1/refunds")
                        .param("refundNo", "RF").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].refundNo").value("RF123"));
    }

    @Test
    void statusHistory_shouldReturnList() throws Exception {
        OrderStatusHistoryVO h = new OrderStatusHistoryVO();
        h.setToStatus(5);
        when(statusHistoryService.admin(eq(1), eq(100L))).thenReturn(List.of(h));

        mockMvc.perform(get("/api/v1/status-history")
                        .param("bizType", "1").param("bizId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].toStatus").value(5));
    }
}
