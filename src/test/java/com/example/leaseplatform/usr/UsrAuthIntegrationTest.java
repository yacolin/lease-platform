package com.example.leaseplatform.usr;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * usr 域认证端到端集成测试（真实 MySQL + Redis + 完整 Security 链）：
 * 后台管理员登录（admin/123456，usr_admins 种子）与微信登录（开发 mock 固定 openid）
 * 双登录 → me → 刷新 → 登出 → 管理端/小程序接口隔离 全流程。
 * 依赖：先执行 ./reset_db.sh 建库并写入种子数据（usr_admins 种子 admin/123456）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsrAuthIntegrationTest {

    /** 开发 mock 固定 openid（wechat.mock-openid） */
    private static final String MOCK_OPENID = "mock_dev_user";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsrUserMapper userMapper;
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

    /** 微信登录并返回 {accessToken, refreshToken} 两个字段 */
    private JsonNode wxLoginTokens() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"dev-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    /** 后台管理员登录（种子 admin / 123456）并返回令牌 */
    private JsonNode adminLoginTokens() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    @Test
    void wxLoginThenMe_shouldWorkWithBearerToken() throws Exception {
        JsonNode tokens = wxLoginTokens();

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("微信用户"))
                .andExpect(jsonPath("$.data.userType").value(3))
                .andExpect(jsonPath("$.data.memberLevel").value(0))
                .andExpect(jsonPath("$.data.balance").value(0.0))
                .andExpect(jsonPath("$.data.giftBalance").value(0.0));

        // 未携带 token → 403
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wxLogin_shouldCreateUserWithFixedMockOpenid() throws Exception {
        wxLoginTokens();

        // 开发 mock 固定复用同一 openid（不会因 code 每次变化而注册新用户）
        UsrUser user = userMapper.selectOne(new LambdaQueryWrapper<UsrUser>()
                .eq(UsrUser::getOpenid, MOCK_OPENID));
        assertThat(user).isNotNull();
        assertThat(user.getUserType()).isEqualTo(3);
        assertThat(user.getStatus()).isEqualTo(1);
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void refresh_shouldRotateAndInvalidateOldToken() throws Exception {
        JsonNode tokens = wxLoginTokens();
        String oldRefresh = tokens.get("refreshToken").asText();

        // 用旧 refresh token 刷新成功 → 新令牌对
        String body = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode newTokens = objectMapper.readTree(body).path("data");

        // 旧 refresh token 已被轮换作废 → 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        // 新 refresh token 可再次刷新
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + newTokens.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void logout_shouldInvalidateRefreshToken() throws Exception {
        JsonNode tokens = wxLoginTokens();
        String refresh = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void updateMe_shouldPersistProfile() throws Exception {
        JsonNode tokens = wxLoginTokens();

        mockMvc.perform(put("/api/v1/me")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"小明\",\"phone\":\"13800138000\",\"avatarUrl\":\"https://x/1.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("小明"))
                .andExpect(jsonPath("$.data.phone").value("13800138000"));

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("小明"))
                .andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://x/1.png"));
    }

    @Test
    void wxLogin_disabledUser_shouldReject() throws Exception {
        wxLoginTokens();

        UsrUser user = userMapper.selectOne(new LambdaQueryWrapper<UsrUser>()
                .eq(UsrUser::getOpenid, MOCK_OPENID));
        assertThat(user).isNotNull();
        user.setStatus(0);
        userMapper.updateById(user);

        mockMvc.perform(post("/api/v1/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"dev-code\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("账号已被禁用"));
    }

    @Test
    void adminLogin_wrongPassword_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void adminLogin_shouldAccessAdminEndpointsButWxUserForbidden() throws Exception {
        JsonNode adminTokens = adminLoginTokens();

        // 管理端接口（prd 商品管理）：admin token 可访问
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + adminTokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(8));

        // 小程序用户 token 访问管理端接口 → 403（类型隔离）
        JsonNode wxTokens = wxLoginTokens();
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + wxTokens.get("accessToken").asText()))
                .andExpect(status().isForbidden());

        // 无 token → 403
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRefresh_shouldRotateAndKeepAdminIdentity() throws Exception {
        JsonNode tokens = adminLoginTokens();
        String oldRefresh = tokens.get("refreshToken").asText();

        // 管理员刷新：轮换成功，新 token 仍是 admin（可访问管理端接口）
        String body = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode newTokens = objectMapper.readTree(body).path("data");

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + newTokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(8));
    }
}
