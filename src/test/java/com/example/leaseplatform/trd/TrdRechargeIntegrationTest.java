package com.example.leaseplatform.trd;

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
 * 交易域端到端集成测试（真实 MySQL + Redis + 完整 Security 链）：
 * 公开档位 → 充值下单 → mock 直充 → 余额/赠送余额入账 → 流水 → 幂等。
 * 依赖：先执行 ./reset_db.sh（trd_recharge_tiers 种子 4 档）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrdRechargeIntegrationTest {

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
    void rechargeFullFlow_shouldCreditBalanceWithGift() throws Exception {
        String token = wxAccessToken();

        // 1. 公开充值档位（种子 4 档）
        mockMvc.perform(get("/api/v1/public/recharge-tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].rechargeAmount").value(20000));

        // 2. 下单（500 档：充值 500 + 赠送 60，未配置微信支付 → prepayParams 为空）
        String orderBody = mockMvc.perform(post("/api/v1/me/recharge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record.paymentStatus").value(0))
                .andExpect(jsonPath("$.data.record.outTradeNo").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long recordId = objectMapper.readTree(orderBody).path("data").path("record").path("id").asLong();

        // 3. mock 直充 → 支付成功
        mockMvc.perform(post("/api/v1/me/recharge/" + recordId + "/mock-pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value(1));

        // 4. 余额 + 赠送余额入账（500 + 60）
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(50000))
                .andExpect(jsonPath("$.data.giftBalance").value(6000));

        // 5. 余额流水 1 条（充值 560）
        mockMvc.perform(get("/api/v1/me/balance-transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].transactionType").value(1))
                .andExpect(jsonPath("$.data.list[0].amount").value(56000));

        // 6. 充值记录 1 条
        mockMvc.perform(get("/api/v1/me/recharge/records")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // 7. 幂等：再次 mock-pay 不重复入账
        mockMvc.perform(post("/api/v1/me/recharge/" + recordId + "/mock-pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(50000))
                .andExpect(jsonPath("$.data.giftBalance").value(6000));

        // 8. DB 校验
        UsrUser user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UsrUser>()
                .eq(UsrUser::getOpenid, "mock_dev_user"));
        assertThat(user.getBalance()).isEqualTo(50000L);
        assertThat(user.getGiftBalance()).isEqualTo(6000L);
    }

    @Test
    void adminRechargeTierCrud_shouldWork() throws Exception {
        String adminToken = adminAccessToken();

        // 创建档位（100 充 10 送）
        String createBody = mockMvc.perform(post("/api/v1/recharge-tiers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rechargeAmount\":10000,\"bonusAmount\":1000,\"equivalentDiscount\":0.9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actualAmount").value(11000))
                .andReturn().getResponse().getContentAsString();
        long tierId = objectMapper.readTree(createBody).path("data").path("id").asLong();

        // 更新
        mockMvc.perform(put("/api/v1/recharge-tiers/" + tierId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rechargeAmount\":10000,\"bonusAmount\":1500,\"equivalentDiscount\":0.88}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actualAmount").value(11500));

        // 删除
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/recharge-tiers/" + tierId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 重复金额 → 409
        mockMvc.perform(post("/api/v1/recharge-tiers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rechargeAmount\":20000,\"bonusAmount\":2000,\"equivalentDiscount\":0.91}"))
                .andExpect(status().isConflict());

        // 小程序 token 访问管理端 → 403
        String wxToken = wxAccessToken();
        mockMvc.perform(get("/api/v1/recharge-tiers")
                        .header("Authorization", "Bearer " + wxToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void notify_unconfigured_shouldReturnFail() throws Exception {
        mockMvc.perform(post("/api/v1/wx/payments/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FAIL"));
    }
}
