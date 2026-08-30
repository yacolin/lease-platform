package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.mtg.dto.RoomVO;
import com.example.leaseplatform.mtg.service.RoomService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会议室管理（管理端）Web 层测试。
 */
@WebMvcTest(RoomAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@WithMockUser(roles = "ADMIN")
class RoomAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private RoomVO vo() {
        RoomVO vo = new RoomVO();
        vo.setId(1L);
        vo.setRoomName("会议室A");
        vo.setHourlyFee(new BigDecimal("80.00"));
        return vo;
    }

    @Test
    void create_shouldReturnVo() throws Exception {
        when(roomService.create(any())).thenReturn(vo());

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\"会议室A\",\"capacity\":10,\"hourlyFee\":80.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomName").value("会议室A"));
    }

    @Test
    void create_missingCapacity_shouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\"会议室A\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void page_shouldReturnPaged() throws Exception {
        when(roomService.page(1, 10, null, null)).thenReturn(PageResult.of(1, List.of(vo())));

        mockMvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void update_shouldReturnVo() throws Exception {
        when(roomService.update(eq(1L), any())).thenReturn(vo());

        mockMvc.perform(put("/api/v1/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\"会议室A\",\"capacity\":10,\"hourlyFee\":80.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void delete_shouldSucceed() throws Exception {
        doNothing().when(roomService).delete(1L);

        mockMvc.perform(delete("/api/v1/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
