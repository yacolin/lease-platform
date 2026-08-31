package com.example.leaseplatform.ord.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.ord.dto.OrderStatsVO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单管理（商家后台）Web 层测试。
 */
@WebMvcTest(OrderAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class OrderAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    /** 管理员登录态（user_type=1 → ROLE_ADMIN，UserContext 可取到操作人 ID） */
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

    private OrderVO orderVO() {
        OrderVO vo = new OrderVO();
        vo.setId(100L);
        vo.setOrderNo("CO123");
        vo.setOrderStatus(1);
        vo.setPayableAmount(2400L);
        return vo;
    }

    @Test
    void page_shouldReturnPaged() throws Exception {
        when(orderService.adminPage(1, 10, null, 1, null))
                .thenReturn(PageResult.of(1, List.of(orderVO())));

        mockMvc.perform(get("/api/v1/orders").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void get_shouldReturnDetail() throws Exception {
        when(orderService.adminGet(100L)).thenReturn(orderVO());

        mockMvc.perform(get("/api/v1/orders/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("CO123"));
    }

    @Test
    void updateStatus_shouldReturnVo() throws Exception {
        OrderVO vo = orderVO();
        vo.setOrderStatus(2);
        when(orderService.adminUpdateStatus(eq(100L), eq(2), eq(9L))).thenReturn(vo);

        mockMvc.perform(put("/api/v1/orders/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderStatus\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(2));
    }

    @Test
    void updateStatus_invalid_shouldReturn422() throws Exception {
        mockMvc.perform(put("/api/v1/orders/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderStatus\":9}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void verifyPickup_shouldReturnVo() throws Exception {
        OrderVO vo = orderVO();
        vo.setOrderStatus(3);
        when(orderService.verifyPickup("123456", 9L)).thenReturn(vo);

        mockMvc.perform(post("/api/v1/orders/verify-pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupCode\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(3));
    }

    @Test
    void stats_shouldReturnStats() throws Exception {
        OrderStatsVO stats = new OrderStatsVO();
        stats.setTodayOrders(5L);
        stats.setTodayAmount(12000L);
        when(orderService.stats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/orders/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayOrders").value(5));
    }
}
