package com.example.leaseplatform.usr;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.leaseplatform.security.JwtTokenProvider;
import com.example.leaseplatform.usr.entity.UsrEnterprise;
import com.example.leaseplatform.usr.entity.UsrEnterpriseMember;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrEnterpriseMapper;
import com.example.leaseplatform.usr.mapper.UsrEnterpriseMemberMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 企业域端到端集成测试（真实 MySQL + Redis + 完整 Security 链）：
 * 注册 → 邀请 → 审核 → 加入 → 会员购买（mock 支付）生效 全流程。
 * 依赖：先执行 ./reset_db.sh（usr_admins 种子 admin/123456、usr_member_levels 3 档）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsrEnterpriseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsrUserMapper userMapper;
    @Autowired
    private UsrEnterpriseMapper enterpriseMapper;
    @Autowired
    private UsrEnterpriseMemberMapper memberMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private JwtTokenProvider tokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanupRedis() {
        Set<String> keys = redisTemplate.keys("auth:refresh:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private JsonNode wxLoginTokens() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"dev\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode adminLoginTokens() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private String registerBody() {
        return """
                {"enterpriseName":"测试企业","unifiedSocialCreditCode":"91110000TEST1",
                 "businessLicenseUrl":"https://x/license.png","legalPersonName":"张三",
                 "legalPersonIdCardFront":"https://x/f.png","legalPersonIdCardBack":"https://x/b.png",
                 "contactName":"张三","contactPhone":"13800138000"}""";
    }

    /** 构造一个指定手机号的微信用户（mock openid 固定，第二个用户需直接造数） */
    private UsrUser createWxUser(String openid, String phone) {
        UsrUser u = new UsrUser();
        u.setOpenid(openid);
        u.setNickname("用户" + phone);
        u.setPhone(phone);
        u.setUserType(3);
        u.setMemberLevel(0);
        u.setIsEnterpriseAdmin(0);
        u.setStatus(1);
        userMapper.insert(u);
        return u;
    }

    @Test
    void fullFlow_registerAuditInviteJoinPurchase_shouldWork() throws Exception {
        // 1. A（mock_dev_user）登录并设置手机号
        JsonNode aTokens = wxLoginTokens();
        String aToken = aTokens.get("accessToken").asText();
        mockMvc.perform(put("/api/v1/me")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk());

        // 2. A 注册企业 → 待审核
        String regBody = mockMvc.perform(post("/api/v1/me/enterprise")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0))
                .andReturn().getResponse().getContentAsString();
        long enterpriseId = objectMapper.readTree(regBody).path("data").path("id").asLong();

        // 重复注册 → 409
        mockMvc.perform(post("/api/v1/me/enterprise")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andExpect(status().isConflict());

        // 3. 公开会员等级列表
        mockMvc.perform(get("/api/v1/public/member-levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].levelCode").value("BASIC"));

        // 4. admin 审核通过
        JsonNode adminTokens = adminLoginTokens();
        String adminToken = adminTokens.get("accessToken").asText();
        mockMvc.perform(get("/api/v1/enterprises").param("auditStatus", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(put("/api/v1/enterprises/" + enterpriseId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(1));

        // 5. 员工 B（造数）被 A 邀请
        UsrUser bUser = createWxUser("mock_user_b", "13900139000");
        String bToken = tokenProvider.createAccessToken(bUser.getId(), 3);
        mockMvc.perform(post("/api/v1/me/enterprise/members")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900139000\"}"))
                .andExpect(status().isOk());

        // 6. B 的待处理邀请 → 接受 → 加入企业
        String invitesBody = mockMvc.perform(get("/api/v1/me/enterprise/invites")
                        .header("Authorization", "Bearer " + bToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        long inviteId = objectMapper.readTree(invitesBody).path("data").get(0).path("id").asLong();
        mockMvc.perform(post("/api/v1/me/enterprise/invites/" + inviteId + "/accept")
                        .header("Authorization", "Bearer " + bToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/enterprise")
                        .header("Authorization", "Bearer " + bToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseName").value("测试企业"))
                .andExpect(jsonPath("$.data.auditStatus").value(1));

        // 7. A 购买 VIP 并 mock 支付 → 企业等级生效、B 同步
        String purchaseBody = mockMvc.perform(post("/api/v1/me/enterprise/member-purchases")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberLevelId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value(0))
                .andReturn().getResponse().getContentAsString();
        long purchaseId = objectMapper.readTree(purchaseBody).path("data").path("id").asLong();
        mockMvc.perform(post("/api/v1/me/enterprise/member-purchases/" + purchaseId + "/mock-pay")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value(1));

        // 企业 + 员工会员等级生效
        mockMvc.perform(get("/api/v1/me/enterprise")
                        .header("Authorization", "Bearer " + bToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberLevel").value(2));
        assertThat(userMapper.selectById(bUser.getId()).getMemberLevel()).isEqualTo(2);
        UsrEnterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        assertThat(enterprise.getMemberLevel()).isEqualTo(2);
        assertThat(enterprise.getMemberExpireAt()).isNotNull();

        // 8. 员工列表 / 移除 B / B 关联清空
        mockMvc.perform(get("/api/v1/me/enterprise/members")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(delete("/api/v1/me/enterprise/members/" + bUser.getId())
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk());
        assertThat(userMapper.selectById(bUser.getId()).getEnterpriseId()).isNull();
    }

    @Test
    void auditReject_shouldReleaseAndAllowReregister() throws Exception {
        JsonNode aTokens = wxLoginTokens();
        String aToken = aTokens.get("accessToken").asText();
        mockMvc.perform(put("/api/v1/me")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk());

        String regBody = mockMvc.perform(post("/api/v1/me/enterprise")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long enterpriseId = objectMapper.readTree(regBody).path("data").path("id").asLong();

        JsonNode adminTokens = adminLoginTokens();
        String adminToken = adminTokens.get("accessToken").asText();

        // 企业未通过审核前不可邀请员工 → 409
        mockMvc.perform(post("/api/v1/me/enterprise/members")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900139000\"}"))
                .andExpect(status().isConflict());

        // 拒绝但无原因 → 400
        mockMvc.perform(put("/api/v1/enterprises/" + enterpriseId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":2}"))
                .andExpect(status().isBadRequest());

        // 带原因拒绝 → 状态 2
        mockMvc.perform(put("/api/v1/enterprises/" + enterpriseId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":2,\"auditReason\":\"资料不完整\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(2));

        // 成员关系被清理 → A 可重新注册
        assertThat(memberMapper.selectList(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getEnterpriseId, enterpriseId))).isEmpty();
        mockMvc.perform(post("/api/v1/me/enterprise")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody().replace("TEST1", "TEST2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void wxUser_shouldBeForbiddenFromAdminEndpoints() throws Exception {
        JsonNode wxTokens = wxLoginTokens();
        mockMvc.perform(get("/api/v1/enterprises")
                        .header("Authorization", "Bearer " + wxTokens.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }
}
