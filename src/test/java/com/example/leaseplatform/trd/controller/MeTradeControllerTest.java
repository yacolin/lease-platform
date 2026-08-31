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
 * 我的交易（1.2）Web 层测试：支付单 / 退款单 / 状态历史。
 */
@WebMvcTest(MeTradeController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class MeTradeControllerTest {

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
    void payments_shouldReturnPaged() throws Exception {
        PaymentVO vo = new PaymentVO();
        vo.setId(800L);
        vo.setPaymentNo("PAY123");
        vo.setAmount(2400L);
        vo.setStatus(1);
        when(paymentService.myPayments(eq(1L), eq(1), eq(10), eq(2)))
                .thenReturn(PageResult.of(1, List.of(vo)));

        mockMvc.perform(get("/api/v1/me/payments").param("bizType", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].paymentNo").value("PAY123"));
    }

    @Test
    void refunds_shouldReturnPaged() throws Exception {
        RefundVO vo = new RefundVO();
        vo.setId(700L);
        vo.setRefundNo("RF123");
        vo.setRefundAmount(1000L);
        vo.setStatus(1);
        when(refundService.myRefunds(eq(1L), eq(1), eq(10)))
                .thenReturn(PageResult.of(1, List.of(vo)));

        mockMvc.perform(get("/api/v1/me/refunds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].refundNo").value("RF123"));
    }

    @Test
    void statusHistory_shouldReturnList() throws Exception {
        OrderStatusHistoryVO h = new OrderStatusHistoryVO();
        h.setToStatus(1);
        when(statusHistoryService.mine(eq(1L), eq(1), eq(100L)))
                .thenReturn(List.of(h));

        mockMvc.perform(get("/api/v1/me/status-history")
                        .param("bizType", "1").param("bizId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].toStatus").value(1));
    }
}
