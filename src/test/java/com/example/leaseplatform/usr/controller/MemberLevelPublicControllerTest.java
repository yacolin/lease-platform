package com.example.leaseplatform.usr.controller;

import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.usr.dto.MemberLevelVO;
import com.example.leaseplatform.usr.service.MemberPurchaseService;
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
 * 会员等级公开列表 Web 层测试（/api/v1/public/member-levels 白名单）。
 */
@WebMvcTest(MemberLevelPublicController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class MemberLevelPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberPurchaseService purchaseService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void list_shouldReturnEnabledLevels() throws Exception {
        MemberLevelVO vo = new MemberLevelVO();
        vo.setId(1L);
        vo.setLevelCode("BASIC");
        vo.setLevelName("基础版");
        vo.setPrice(new BigDecimal("0.00"));
        when(purchaseService.publicLevels()).thenReturn(List.of(vo));

        mockMvc.perform(get("/api/v1/public/member-levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].levelCode").value("BASIC"))
                .andExpect(jsonPath("$.data[0].price").value(0.0));
    }
}
