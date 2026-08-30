package com.example.leaseplatform.ord.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.ord.dto.MealReservationVO;
import com.example.leaseplatform.ord.service.MealReservationService;
import com.example.leaseplatform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 正餐预订管理（商家后台）Web 层测试。
 */
@WebMvcTest(MealReservationAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@WithMockUser(roles = "ADMIN")
class MealReservationAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MealReservationService reservationService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void page_shouldReturnPaged() throws Exception {
        MealReservationVO vo = new MealReservationVO();
        vo.setId(300L);
        vo.setStatus(1);
        when(reservationService.adminPage(1, 10, null, 1))
                .thenReturn(PageResult.of(1, List.of(vo)));

        mockMvc.perform(get("/api/v1/meal-reservations").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void page_withDate_shouldWork() throws Exception {
        when(reservationService.adminPage(1, 10, LocalDate.now().plusDays(3), null))
                .thenReturn(PageResult.of(0, List.of()));

        mockMvc.perform(get("/api/v1/meal-reservations").param("date", LocalDate.now().plusDays(3).toString()))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatus_shouldReturnVo() throws Exception {
        MealReservationVO vo = new MealReservationVO();
        vo.setId(300L);
        vo.setStatus(2);
        when(reservationService.adminUpdateStatus(eq(300L), eq(2))).thenReturn(vo);

        mockMvc.perform(put("/api/v1/meal-reservations/300/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderStatus\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));
    }

    @Test
    void updateStatus_invalid_shouldReturn422() throws Exception {
        mockMvc.perform(put("/api/v1/meal-reservations/300/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderStatus\":9}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
