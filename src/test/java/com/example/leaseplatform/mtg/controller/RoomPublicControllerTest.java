package com.example.leaseplatform.mtg.controller;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会议室公开列表 Web 层测试（白名单）。
 */
@WebMvcTest(RoomPublicController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class RoomPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void list_shouldReturnRooms() throws Exception {
        RoomVO vo = new RoomVO();
        vo.setId(1L);
        vo.setRoomName("会议室A");
        vo.setHourlyFee(new BigDecimal("80.00"));
        when(roomService.publicList()).thenReturn(List.of(vo));

        mockMvc.perform(get("/api/v1/public/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roomName").value("会议室A"));
    }
}
