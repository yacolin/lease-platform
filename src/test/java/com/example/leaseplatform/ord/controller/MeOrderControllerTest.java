package com.example.leaseplatform.ord.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.ord.dto.OrderVO;
import com.example.leaseplatform.ord.service.OrderService;
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
 * 我的订单 Web 层测试（经 UserContext 取当前用户，预置 LoginUser）。
 */
@WebMvcTest(MeOrderController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class MeOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;
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

    private OrderVO orderVO() {
        OrderVO vo = new OrderVO();
        vo.setId(100L);
        vo.setOrderNo("CO123");
        vo.setOrderStatus(0);
        vo.setPayableAmount(2400L);
        return vo;
    }

    @Test
    void create_shouldReturnOrder() throws Exception {
        when(orderService.create(eq(1L), any())).thenReturn(orderVO());

        mockMvc.perform(post("/api/v1/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":1,"quantity":2,"spec":{"cup_size":"大杯"}}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("CO123"))
                .andExpect(jsonPath("$.data.payableAmount").value(2400));
    }

    @Test
    void create_emptyItems_shouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/me/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void pay_shouldReturnPaidOrder() throws Exception {
        OrderVO vo = orderVO();
        vo.setOrderStatus(1);
        vo.setPickupCode("123456");
        when(orderService.pay(1L, 100L)).thenReturn(vo);

        mockMvc.perform(post("/api/v1/me/orders/100/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pickupCode").value("123456"));
    }

    @Test
    void cancel_shouldReturnCancelledOrder() throws Exception {
        OrderVO vo = orderVO();
        vo.setOrderStatus(4);
        when(orderService.cancel(1L, 100L, "不要了")).thenReturn(vo);

        mockMvc.perform(post("/api/v1/me/orders/100/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"不要了\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(4));
    }

    @Test
    void myOrders_shouldReturnPaged() throws Exception {
        when(orderService.myOrders(1L, 1, 10, null)).thenReturn(PageResult.of(1, List.of(orderVO())));

        mockMvc.perform(get("/api/v1/me/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void get_shouldReturnDetail() throws Exception {
        when(orderService.getMine(1L, 100L)).thenReturn(orderVO());

        mockMvc.perform(get("/api/v1/me/orders/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));
    }
}
