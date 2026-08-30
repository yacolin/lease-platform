package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.mtg.dto.RoomLevelPriceVO;
import com.example.leaseplatform.mtg.service.RoomLevelPriceService;
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
 * 会议室等级定价管理（管理端）Web 层测试。
 */
@WebMvcTest(RoomLevelPriceAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@WithMockUser(roles = "ADMIN")
class RoomLevelPriceAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomLevelPriceService priceService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private RoomLevelPriceVO vo() {
        RoomLevelPriceVO vo = new RoomLevelPriceVO();
        vo.setId(1L);
        vo.setRoomId(1L);
        vo.setRoomName("会议室A");
        vo.setLevelCode("VIP");
        vo.setOvertimeFee(10000L);
        return vo;
    }

    @Test
    void create_shouldReturnVo() throws Exception {
        when(priceService.create(any())).thenReturn(vo());

        mockMvc.perform(post("/api/v1/room-level-prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"levelCode\":\"VIP\",\"overtimeFee\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.levelCode").value("VIP"))
                .andExpect(jsonPath("$.data.overtimeFee").value(10000));
    }

    @Test
    void create_invalidLevel_shouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/room-level-prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"levelCode\":\"GOLD\",\"overtimeFee\":10000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void page_shouldReturnPaged() throws Exception {
        when(priceService.page(1, 10, null)).thenReturn(PageResult.of(1, List.of(vo())));

        mockMvc.perform(get("/api/v1/room-level-prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void listByRoom_shouldReturnList() throws Exception {
        when(priceService.listByRoom(1L)).thenReturn(List.of(vo()));

        mockMvc.perform(get("/api/v1/room-level-prices/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void update_shouldReturnVo() throws Exception {
        when(priceService.update(eq(1L), any())).thenReturn(vo());

        mockMvc.perform(put("/api/v1/room-level-prices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"levelCode\":\"VIP\",\"overtimeFee\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void delete_shouldSucceed() throws Exception {
        doNothing().when(priceService).delete(1L);

        mockMvc.perform(delete("/api/v1/room-level-prices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
