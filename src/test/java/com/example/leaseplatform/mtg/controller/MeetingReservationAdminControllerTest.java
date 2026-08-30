package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.mtg.dto.MeetingReservationVO;
import com.example.leaseplatform.mtg.service.MeetingReservationService;
import com.example.leaseplatform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
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
@WithMockUser(roles = "ADMIN")
class MeetingReservationAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeetingReservationService reservationService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

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
    void complete_shouldReturnCompleted() throws Exception {
        MeetingReservationVO vo = new MeetingReservationVO();
        vo.setId(100L);
        vo.setStatus(2);
        when(reservationService.adminComplete(eq(100L))).thenReturn(vo);

        mockMvc.perform(put("/api/v1/meeting-reservations/100/complete"))
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
