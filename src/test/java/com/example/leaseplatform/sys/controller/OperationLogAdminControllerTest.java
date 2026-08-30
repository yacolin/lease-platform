package com.example.leaseplatform.sys.controller;

import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.config.SecurityConfig;
import com.example.leaseplatform.config.SecurityProperties;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.sys.dto.OperationLogVO;
import com.example.leaseplatform.sys.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 操作日志查询（管理端）Web 层测试。
 */
@WebMvcTest(OperationLogAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@WithMockUser(roles = "ADMIN")
class OperationLogAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperationLogService operationLogService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void page_shouldReturnPaged() throws Exception {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(1L);
        vo.setOperationType("审核企业");
        when(operationLogService.page(1, 10, 1L, "审核"))
                .thenReturn(PageResult.of(1, List.of(vo)));

        mockMvc.perform(get("/api/v1/operation-logs").param("operatorId", "1").param("operationType", "审核"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].operationType").value("审核企业"));
    }
}
