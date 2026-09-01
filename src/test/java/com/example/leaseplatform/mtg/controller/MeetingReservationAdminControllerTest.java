package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会议室预约管理（商家后台）Web 层测试。
 */
@WebMvcTest(MeetingReservationAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class MeetingReservationAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeetingReservationService reservationService;
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

    @Test
    void page_shouldReturnPaged() throws Exception {
        MeetingReservationVO vo = new MeetingReservationVO();
        vo.setId(100L);
        vo.setStatus(1);
        when(reservationService.adminPage(1, 10, null, null, 1))
                .thenReturn(PageResult.of(1, List.of(vo)));

        mockMvc.perform(get("/api/v1/meeting-reservations").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void get_shouldReturnDetail() throws Exception {
        MeetingReservationVO vo = new MeetingReservationVO();
        vo.setId(100L);
        vo.setRoomName("会议室A");
        when(reservationService.adminGet(100L)).thenReturn(vo);

        mockMvc.perform(get("/api/v1/meeting-reservations/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomName").value("会议室A"));
    }

    @Test
    void updateStatus_shouldReturnVo() throws Exception {
        MeetingReservationVO vo = new MeetingReservationVO();
        vo.setId(100L);
        vo.setStatus(2);
        when(reservationService.adminUpdateStatus(eq(100L), eq(2), eq(9L))).thenReturn(vo);

        mockMvc.perform(put("/api/v1/meeting-reservations/100/status")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"orderStatus\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));
    }

    @Test
    void page_withDate_shouldWork() throws Exception {
        when(reservationService.adminPage(1, 10, LocalDate.now().plusDays(2), null, null))
                .thenReturn(PageResult.of(0, List.of()));

        mockMvc.perform(get("/api/v1/meeting-reservations").param("date", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isOk());
    }
}
