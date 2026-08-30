package com.example.leaseplatform.trd.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.trd.dto.BalanceTransactionVO;
import com.example.leaseplatform.trd.dto.RechargeCreateResultVO;
import com.example.leaseplatform.trd.dto.RechargeRecordVO;
import com.example.leaseplatform.trd.service.BalanceService;
import com.example.leaseplatform.trd.service.RechargeService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 我的充值/余额 Web 层测试。
 * 控制器经 UserContext 取当前用户（需 LoginUser principal），预置 SecurityContext 模拟登录。
 */
@WebMvcTest(MeRechargeController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class MeRechargeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RechargeService rechargeService;
    @MockitoBean
    private BalanceService balanceService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUpAuth() {
        com.example.leaseplatform.security.LoginUser loginUser =
                com.example.leaseplatform.security.LoginUser.of(1L, 3);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recharge_shouldReturnCreateResult() throws Exception {
        RechargeCreateResultVO result = new RechargeCreateResultVO();
        RechargeRecordVO record = new RechargeRecordVO();
        record.setId(100L);
        record.setOutTradeNo("RC123");
        record.setPaymentStatus(0);
        result.setRecord(record);
        when(rechargeService.createRecharge(any(), eq(1L))).thenReturn(result);

        mockMvc.perform(post("/api/v1/me/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record.outTradeNo").value("RC123"));
    }

    @Test
    void recharge_missingTier_shouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/me/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void mockPay_shouldReturnPaidRecord() throws Exception {
        RechargeRecordVO record = new RechargeRecordVO();
        record.setId(100L);
        record.setPaymentStatus(1);
        when(rechargeService.mockPay(any(), eq(100L))).thenReturn(record);

        mockMvc.perform(post("/api/v1/me/recharge/100/mock-pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value(1));
    }

    @Test
    void query_shouldReturnRecord() throws Exception {
        RechargeRecordVO record = new RechargeRecordVO();
        record.setId(100L);
        record.setPaymentStatus(1);
        when(rechargeService.query(any(), eq(100L))).thenReturn(record);

        mockMvc.perform(post("/api/v1/me/recharge/100/query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value(1));
    }

    @Test
    void records_shouldReturnPaged() throws Exception {
        RechargeRecordVO record = new RechargeRecordVO();
        record.setId(100L);
        record.setPaymentStatus(1);
        when(rechargeService.myRecords(any(), eq(1), eq(10))).thenReturn(PageResult.of(1, List.of(record)));

        mockMvc.perform(get("/api/v1/me/recharge/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void balanceTransactions_shouldReturnPaged() throws Exception {
        BalanceTransactionVO tx = new BalanceTransactionVO();
        tx.setId(1L);
        tx.setTransactionType(1);
        tx.setAmount(22000L);
        when(balanceService.myTransactions(any(), eq(1), eq(10))).thenReturn(PageResult.of(1, List.of(tx)));

        mockMvc.perform(get("/api/v1/me/balance-transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].amount").value(22000));
    }
}
