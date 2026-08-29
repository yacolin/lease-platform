package com.example.leaseplatform.usr.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.usr.dto.EnterpriseVO;
import com.example.leaseplatform.usr.service.EnterpriseService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 企业审核（管理端）Web 层测试。
 * /api/v1/enterprises/** 在 admin-paths 内，需 ROLE_ADMIN。
 */
@WebMvcTest(EnterpriseAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@WithMockUser(roles = "ADMIN")
class EnterpriseAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnterpriseService enterpriseService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private EnterpriseVO enterpriseVO() {
        EnterpriseVO vo = new EnterpriseVO();
        vo.setId(1L);
        vo.setEnterpriseName("测试企业");
        vo.setAuditStatus(0);
        return vo;
    }

    @Test
    void page_shouldReturnPagedResult() throws Exception {
        when(enterpriseService.page(1, 10, 0, null))
                .thenReturn(PageResult.of(1, List.of(enterpriseVO())));

        mockMvc.perform(get("/api/v1/enterprises").param("auditStatus", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].enterpriseName").value("测试企业"));
    }

    @Test
    void get_shouldReturnVo() throws Exception {
        when(enterpriseService.getById(1L)).thenReturn(enterpriseVO());

        mockMvc.perform(get("/api/v1/enterprises/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void audit_shouldReturnVo() throws Exception {
        when(enterpriseService.audit(eq(1L), any())).thenReturn(enterpriseVO());

        mockMvc.perform(put("/api/v1/enterprises/1/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void audit_invalidStatus_shouldReturn422() throws Exception {
        mockMvc.perform(put("/api/v1/enterprises/1/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":9}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
