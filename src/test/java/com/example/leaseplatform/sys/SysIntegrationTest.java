package com.example.leaseplatform.sys;

import com.example.leaseplatform.sys.mapper.SysOperationLogMapper;
import com.example.leaseplatform.sys.service.NotificationService;
import com.example.leaseplatform.usr.dto.EnterpriseRegisterReq;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统域端到端集成测试（真实 MySQL + Redis + 完整 Security 链）：
 * 通知中心（发送→列表→未读数→已读）与操作日志（审核企业触发 AOP 记录→查询）。
 * 依赖：先执行 ./reset_db.sh（usr_admins 种子 admin/123456）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SysIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private UsrUserMapper userMapper;
    @Autowired
    private SysOperationLogMapper operationLogMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanupRedis() {
        Set<String> keys = redisTemplate.keys("auth:refresh:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String wxAccessToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"dev\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private String adminAccessToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    @Test
    void notificationFlow_shouldWork() throws Exception {
        String token = wxAccessToken();
        UsrUser user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UsrUser>()
                .eq(UsrUser::getOpenid, "mock_dev_user"));

        // 服务层发送两条通知（业务事件触发入口）
        notificationService.send(user.getId(), NotificationService.TYPE_AUDIT_PASS, "企业审核通过", "您的企业已通过审核");
        notificationService.send(user.getId(), NotificationService.TYPE_RECHARGE, "充值成功", "余额已到账");

        // 列表 2 条 + 未读数 2
        mockMvc.perform(get("/api/v1/me/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        mockMvc.perform(get("/api/v1/me/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));

        // 单条已读 → 未读 1
        String listBody = mockMvc.perform(get("/api/v1/me/notifications")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        long firstId = objectMapper.readTree(listBody).path("data").path("list").get(0).path("id").asLong();
        mockMvc.perform(put("/api/v1/me/notifications/" + firstId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        // 全部已读 → 未读 0；仅未读列表为空
        mockMvc.perform(put("/api/v1/me/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/notifications").param("unreadOnly", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void adminAudit_shouldTriggerOperationLog() throws Exception {
        // 用户注册企业
        String token = wxAccessToken();
        mockMvc.perform(put("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk());
        String regBody = mockMvc.perform(post("/api/v1/me/enterprise")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enterpriseName":"日志测试企业","unifiedSocialCreditCode":"LOG001",
                                 "businessLicenseUrl":"https://x/l.png","legalPersonName":"张三",
                                 "legalPersonIdCardFront":"https://x/f.png","legalPersonIdCardBack":"https://x/b.png",
                                 "contactName":"张三","contactPhone":"13800138000"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long enterpriseId = objectMapper.readTree(regBody).path("data").path("id").asLong();

        // admin 审核（@OperationLog 注解 → AOP 记录日志）
        String adminToken = adminAccessToken();
        mockMvc.perform(put("/api/v1/enterprises/" + enterpriseId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(1));

        // 操作日志已写入 + 查询可见
        assertThat(operationLogMapper.selectCount(null)).isEqualTo(1L);
        mockMvc.perform(get("/api/v1/operation-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].operationType").value("审核企业"))
                .andExpect(jsonPath("$.data.list[0].operatorId").value(1));
    }
}
