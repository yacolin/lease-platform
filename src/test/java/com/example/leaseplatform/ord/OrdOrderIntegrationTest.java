package com.example.leaseplatform.ord;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单域端到端集成测试（真实 MySQL + Redis + 完整 Security 链）：
 * 充值（获得充值折扣率）→ 咖啡下单（折扣叠加）→ 余额支付（取餐码）→
 * 商家状态推进/核销 → 取消退款 → 统计。
 * 依赖：先执行 ./reset_db.sh（商品种子 美式 12 元等、充值档位 4 档、usr_admins admin）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrdOrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
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

    /** 充值 500 档（赠送 60，折扣率 0.89） */
    private void recharge(String token) throws Exception {
        String orderBody = mockMvc.perform(post("/api/v1/me/recharge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":2}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long recordId = objectMapper.readTree(orderBody).path("data").path("record").path("id").asLong();
        mockMvc.perform(post("/api/v1/me/recharge/" + recordId + "/mock-pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void coffeeOrderFullFlow_shouldWork() throws Exception {
        String token = wxAccessToken();
        recharge(token); // 充值 500 → 余额 500 + 赠送 60，充值折扣率 0.89（非会员）

        // 1. 下单：美式 12×2 + 拿铁 15×1 → 原价 39；非会员 × 充值 0.89 → 应付 34.71
        String orderBody = mockMvc.perform(post("/api/v1/me/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"productId":1,"quantity":2,"spec":{"cup_size":"大杯","temperature":"热"}},
                                  {"productId":2,"quantity":1,"spec":{"cup_size":"中杯"}}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(3900))
                .andExpect(jsonPath("$.data.memberDiscount").value(0))
                .andExpect(jsonPath("$.data.rechargeDiscount").value(429))
                .andExpect(jsonPath("$.data.payableAmount").value(3471))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].specification.cup_size").value("大杯"))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderBody).path("data").path("id").asLong();

        // 2. 余额支付 → 待取餐 + 取餐码
        String payBody = mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(1))
                .andExpect(jsonPath("$.data.pickupCode").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String pickupCode = objectMapper.readTree(payBody).path("data").path("pickupCode").asText();

        // 3. 余额扣减：debit 赠送余额优先 → 赠送扣 34.71（60−34.71=25.29），余额不动
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(50000))
                .andExpect(jsonPath("$.data.giftBalance").value(2529));

        // 4. 我的订单列表
        mockMvc.perform(get("/api/v1/me/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // 5. 商家：列表 → 状态推进 1→2 → 取餐码核销 → 3 完成
        String adminToken = adminAccessToken();
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(put("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderStatus\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(2));
        mockMvc.perform(post("/api/v1/orders/verify-pickup")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupCode\":\"" + pickupCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(3));

        // 6. 统计
        mockMvc.perform(get("/api/v1/orders/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayOrders").value(1))
                .andExpect(jsonPath("$.data.todayAmount").value(3471));
    }

    @Test
    void cancelPaidOrder_shouldRefundBalance() throws Exception {
        String token = wxAccessToken();
        recharge(token);

        // 下单 + 支付
        String orderBody = mockMvc.perform(post("/api/v1/me/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderBody).path("data").path("id").asLong();
        mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 取消 → 原路退款：支付 10.68 从赠送余额扣（gift 60−10.68=49.32），
        // 退款入余额（balance 500+10.68=510.68，总资产不变）
        mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"不想要了\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(4));

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(51068))
                .andExpect(jsonPath("$.data.giftBalance").value(4932));

        // 退款流水（充值 1 + 消费 1 + 退款 1 = 3 条）
        mockMvc.perform(get("/api/v1/me/balance-transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    void order_insufficientBalance_shouldFailPay() throws Exception {
        String token = wxAccessToken(); // 未充值，余额 0

        String orderBody = mockMvc.perform(post("/api/v1/me/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderBody).path("data").path("id").asLong();

        // 余额不足 → 409，订单保持待支付
        mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("余额不足"));
        mockMvc.perform(get("/api/v1/me/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(0));
    }

    @Test
    void coffeeOrder_withSku_shouldSnapshotSkuFields() throws Exception {
        // 1.3：指定 SKU 下单 → 明细携带 SKU 快照（sku_id/编码/单价/规格快照）
        String token = wxAccessToken();
        mockMvc.perform(post("/api/v1/me/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"skuId\":10001,\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].skuId").value(10001))
                .andExpect(jsonPath("$.data.items[0].skuNameSnapshot").value("SKU000101"))
                .andExpect(jsonPath("$.data.items[0].skuPriceSnapshot").value(1200))
                .andExpect(jsonPath("$.data.items[0].specificationSnapshot.cup_size").value("大杯"));
    }
}
