package com.example.leaseplatform.trd.controller;

import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.trd.service.RechargeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 微信支付回调 Web 层测试（白名单）。
 */
@WebMvcTest(WxPaymentNotifyController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class WxPaymentNotifyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RechargeService rechargeService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void notify_shouldReturnSuccess() throws Exception {
        doNothing().when(rechargeService).handleNotify(org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(post("/api/v1/wx/payments/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_type\":\"TRANSACTION.SUCCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    void notify_unconfigured_shouldReturnFail() throws Exception {
        doThrow(new com.example.leaseplatform.common.BizException(
                com.example.leaseplatform.common.ErrorCode.INVALID_PARAMS, "微信支付未配置",
                org.springframework.http.HttpStatus.BAD_REQUEST))
                .when(rechargeService).handleNotify(org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(post("/api/v1/wx/payments/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FAIL"));
    }
}
