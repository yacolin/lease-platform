package com.example.leaseplatform.usr.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.usr.dto.EnterpriseInviteVO;
import com.example.leaseplatform.usr.dto.EnterpriseMemberVO;
import com.example.leaseplatform.usr.dto.EnterpriseVO;
import com.example.leaseplatform.usr.dto.MemberPurchaseVO;
import com.example.leaseplatform.usr.service.EnterpriseService;
import com.example.leaseplatform.usr.service.MemberPurchaseService;
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
 * 我的企业（小程序端）Web 层测试。
 * /api/v1/me/enterprise/** 需登录，通过 @WithMockUser 走真实 Security 链。
 */
@WebMvcTest(MeEnterpriseController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@WithMockUser
class MeEnterpriseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnterpriseService enterpriseService;
    @MockitoBean
    private MemberPurchaseService purchaseService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private EnterpriseVO enterpriseVO() {
        EnterpriseVO vo = new EnterpriseVO();
        vo.setId(1L);
        vo.setEnterpriseName("测试企业");
        vo.setAuditStatus(1);
        return vo;
    }

    @Test
    void register_shouldReturnVo() throws Exception {
        when(enterpriseService.register(any())).thenReturn(enterpriseVO());

        mockMvc.perform(post("/api/v1/me/enterprise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enterpriseName":"测试企业","unifiedSocialCreditCode":"91110000TEST",
                                 "businessLicenseUrl":"https://x/l.png","legalPersonName":"张三",
                                 "legalPersonIdCardFront":"https://x/f.png","legalPersonIdCardBack":"https://x/b.png",
                                 "contactName":"张三","contactPhone":"13800138000"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseName").value("测试企业"));
    }

    @Test
    void register_missingField_shouldReturn422() throws Exception {
        mockMvc.perform(post("/api/v1/me/enterprise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseName\":\"测试企业\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void myEnterprise_shouldReturnVo() throws Exception {
        when(enterpriseService.myEnterprise()).thenReturn(enterpriseVO());

        mockMvc.perform(get("/api/v1/me/enterprise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(1));
    }

    @Test
    void members_shouldReturnList() throws Exception {
        EnterpriseMemberVO m = new EnterpriseMemberVO();
        m.setUserId(2L);
        m.setRole(0);
        m.setInviteStatus(1);
        when(enterpriseService.members()).thenReturn(List.of(m));

        mockMvc.perform(get("/api/v1/me/enterprise/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(2));
    }

    @Test
    void invite_shouldSucceed() throws Exception {
        doNothing().when(enterpriseService).invite(any());

        mockMvc.perform(post("/api/v1/me/enterprise/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900139000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void removeMember_shouldSucceed() throws Exception {
        doNothing().when(enterpriseService).removeMember(2L);

        mockMvc.perform(delete("/api/v1/me/enterprise/members/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void setAdmin_shouldSucceed() throws Exception {
        doNothing().when(enterpriseService).setAdmin(eq(2L), eq(true));

        mockMvc.perform(put("/api/v1/me/enterprise/members/2/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAdmin\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void myInvites_shouldReturnList() throws Exception {
        EnterpriseInviteVO inv = new EnterpriseInviteVO();
        inv.setId(10L);
        inv.setEnterpriseName("邀请企业");
        when(enterpriseService.myInvites()).thenReturn(List.of(inv));

        mockMvc.perform(get("/api/v1/me/enterprise/invites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].enterpriseName").value("邀请企业"));
    }

    @Test
    void acceptInvite_shouldSucceed() throws Exception {
        doNothing().when(enterpriseService).acceptInvite(10L);

        mockMvc.perform(post("/api/v1/me/enterprise/invites/10/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void rejectInvite_shouldSucceed() throws Exception {
        doNothing().when(enterpriseService).rejectInvite(10L);

        mockMvc.perform(post("/api/v1/me/enterprise/invites/10/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void createPurchase_shouldReturnVo() throws Exception {
        MemberPurchaseVO vo = new MemberPurchaseVO();
        vo.setId(500L);
        vo.setMemberLevelCode("VIP");
        vo.setPayPrice(new BigDecimal("5000.00"));
        vo.setPaymentStatus(0);
        when(purchaseService.createPurchase(any())).thenReturn(vo);

        mockMvc.perform(post("/api/v1/me/enterprise/member-purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberLevelId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberLevelCode").value("VIP"));
    }

    @Test
    void mockPay_shouldReturnVo() throws Exception {
        MemberPurchaseVO vo = new MemberPurchaseVO();
        vo.setId(500L);
        vo.setPaymentStatus(1);
        when(purchaseService.mockPay(500L)).thenReturn(vo);

        mockMvc.perform(post("/api/v1/me/enterprise/member-purchases/500/mock-pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value(1));
    }

    @Test
    void myPurchases_shouldReturnPaged() throws Exception {
        MemberPurchaseVO vo = new MemberPurchaseVO();
        vo.setId(500L);
        vo.setPaymentStatus(1);
        when(purchaseService.myPurchases(1, 10)).thenReturn(PageResult.of(1, List.of(vo)));

        mockMvc.perform(get("/api/v1/me/enterprise/member-purchases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
