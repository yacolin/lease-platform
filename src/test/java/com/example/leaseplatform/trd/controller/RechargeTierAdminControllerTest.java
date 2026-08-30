package com.example.leaseplatform.trd.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.trd.dto.RechargeTierVO;
import com.example.leaseplatform.trd.service.RechargeTierService;
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
 * 充值档位管理（管理端）Web 层测试。
 */
@WebMvcTest(RechargeTierAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@WithMockUser(roles = "ADMIN")
class RechargeTierAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RechargeTierService tierService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private RechargeTierVO vo() {
        RechargeTierVO vo = new RechargeTierVO();
        vo.setId(1L);
        vo.setRechargeAmount(20000L);
        vo.setBonusAmount(2000L);
        vo.setActualAmount(22000L);
        return vo;
    }

    @Test
    void create_shouldReturnVo() throws Exception {
        when(tierService.create(any())).thenReturn(vo());

        mockMvc.perform(post("/api/v1/recharge-tiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rechargeAmount\":20000,\"bonusAmount\":2000,\"equivalentDiscount\":0.91}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actualAmount").value(22000));
    }

    @Test
    void create_invalidAmount_shouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/recharge-tiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bonusAmount\":2000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void page_shouldReturnPaged() throws Exception {
        when(tierService.page(1, 10, null)).thenReturn(PageResult.of(1, List.of(vo())));

        mockMvc.perform(get("/api/v1/recharge-tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void update_shouldReturnVo() throws Exception {
        when(tierService.update(eq(1L), any())).thenReturn(vo());

        mockMvc.perform(put("/api/v1/recharge-tiers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rechargeAmount\":20000,\"bonusAmount\":3000,\"equivalentDiscount\":0.9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void delete_shouldSucceed() throws Exception {
        doNothing().when(tierService).delete(1L);

        mockMvc.perform(delete("/api/v1/recharge-tiers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
