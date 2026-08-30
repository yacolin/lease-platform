package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.mtg.dto.MeetingFreeHoursVO;
import com.example.leaseplatform.mtg.dto.MeetingReservationVO;
import com.example.leaseplatform.mtg.service.MeetingReservationService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 我的会议室预约 Web 层测试（经 UserContext 取当前用户）。
 */
@WebMvcTest(MeMeetingReservationController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class MeMeetingReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeetingReservationService reservationService;
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

    private MeetingReservationVO vo() {
        MeetingReservationVO vo = new MeetingReservationVO();
        vo.setId(100L);
        vo.setRoomName("会议室A");
        vo.setFeeAmount(16000L);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_shouldReturnReservation() throws Exception {
        when(reservationService.create(eq(1L), any())).thenReturn(vo());

        mockMvc.perform(post("/api/v1/me/meeting-reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":1,"reservationDate":"%s","startTime":"09:00:00",
                                 "endTime":"11:00:00","meetingTopic":"周会"}"""
                                .formatted(LocalDate.now().plusDays(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(16000));
    }

    @Test
    void create_missingTopic_shouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/me/meeting-reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":1,"reservationDate":"%s","startTime":"09:00:00","endTime":"10:00:00"}"""
                                .formatted(LocalDate.now().plusDays(2))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void pay_shouldReturnConfirmed() throws Exception {
        MeetingReservationVO paid = vo();
        paid.setStatus(1);
        when(reservationService.pay(1L, 100L)).thenReturn(paid);

        mockMvc.perform(post("/api/v1/me/meeting-reservations/100/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));
    }

    @Test
    void cancel_shouldReturnCancelled() throws Exception {
        MeetingReservationVO cancelled = vo();
        cancelled.setStatus(3);
        when(reservationService.cancel(1L, 100L, "改期")).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/me/meeting-reservations/100/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"改期\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(3));
    }

    @Test
    void myReservations_shouldReturnPaged() throws Exception {
        when(reservationService.myReservations(1L, 1, 10, null))
                .thenReturn(PageResult.of(1, List.of(vo())));

        mockMvc.perform(get("/api/v1/me/meeting-reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void freeHours_shouldReturnVo() throws Exception {
        MeetingFreeHoursVO free = new MeetingFreeHoursVO();
        free.setTotalHours(new BigDecimal("4.0"));
        free.setUsedHours(new BigDecimal("1.0"));
        free.setRemainingHours(new BigDecimal("3.0"));
        when(reservationService.freeHours(eq(1L), any())).thenReturn(free);

        mockMvc.perform(get("/api/v1/me/meeting-reservations/free-hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingHours").value(3.0));
    }

    @Test
    void get_shouldReturnDetail() throws Exception {
        when(reservationService.getMine(1L, 100L)).thenReturn(vo());

        mockMvc.perform(get("/api/v1/me/meeting-reservations/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomName").value("会议室A"));
    }
}
