package com.example.leaseplatform.ord.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.ord.dto.MealReservationVO;
import com.example.leaseplatform.ord.service.MealReservationService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 我的正餐预订 Web 层测试（经 UserContext 取当前用户）。
 */
@WebMvcTest(MeMealReservationController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class MeMealReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MealReservationService reservationService;
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

    private MealReservationVO vo() {
        MealReservationVO vo = new MealReservationVO();
        vo.setId(300L);
        vo.setProductName("3荤1素套餐");
        vo.setPayableAmount(3560L);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_shouldReturnReservation() throws Exception {
        when(reservationService.create(eq(1L), any())).thenReturn(vo());

        mockMvc.perform(post("/api/v1/me/meal-reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":4,"menuDate":"%s","timeSlot":"午餐","quantity":2,"deliveryType":1}"""
                                .formatted(LocalDate.now().plusDays(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payableAmount").value(3560));
    }

    @Test
    void create_missingFields_shouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/me/meal-reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":4}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void pay_shouldReturnReady() throws Exception {
        MealReservationVO paid = vo();
        paid.setStatus(1);
        when(reservationService.pay(1L, 300L)).thenReturn(paid);

        mockMvc.perform(post("/api/v1/me/meal-reservations/300/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));
    }

    @Test
    void cancel_shouldReturnCancelled() throws Exception {
        MealReservationVO cancelled = vo();
        cancelled.setStatus(4);
        when(reservationService.cancel(1L, 300L, "不要了")).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/me/meal-reservations/300/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"不要了\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(4));
    }

    @Test
    void myReservations_shouldReturnPaged() throws Exception {
        when(reservationService.myReservations(1L, 1, 10, null))
                .thenReturn(PageResult.of(1, List.of(vo())));

        mockMvc.perform(get("/api/v1/me/meal-reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
