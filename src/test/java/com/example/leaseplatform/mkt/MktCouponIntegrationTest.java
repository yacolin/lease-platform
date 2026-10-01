package com.example.leaseplatform.mkt;

import com.example.leaseplatform.mkt.entity.MktUserCoupon;
import com.example.leaseplatform.mkt.mapper.MktUserCouponMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.example.leaseplatform.support.CacheTestSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 营销域端到端集成测试（1.6 运营/营销）：领券 → 下单用券（折扣叠加 + 订单券快照）→
 * 券核销绑定订单；重复领取 409；门槛不足 400；过期券 400。
 * 依赖：先执行 make db-reset（种子含 3 张券模板：满30减5咖啡券 / 正餐9折券 / 无门槛5元券）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MktCouponIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MktUserCouponMapper userCouponMapper;
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

    @BeforeEach
    void flushCacheNamespace() {
        CacheTestSupport.flushCacheNamespace(redisTemplate);
    }

    @Test
    void couponFullFlow_shouldWork() throws Exception {
        String token = wxAccessToken();

        // 1. 公开可领券列表（种子 3 张）
        mockMvc.perform(get("/api/v1/public/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        // 2. 领取「满30减5咖啡券」（seed id=1）
        String claimBody = mockMvc.perform(post("/api/v1/me/coupons/1/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.couponName").value("满30减5咖啡券"))
                .andExpect(jsonPath("$.data.status").value(0))
                .andReturn().getResponse().getContentAsString();
        long userCouponId = objectMapper.readTree(claimBody).path("data").path("id").asLong();

        // 3. 重复领取 → 409
        mockMvc.perform(post("/api/v1/me/coupons/1/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("已领取过该优惠券"));

        // 4. 门槛不足（美式 12 元 ×1 = 1200 < 3000）→ 400，券未消耗
        mockMvc.perform(post("/api/v1/me/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":1}],\"couponId\":" + userCouponId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("未满足优惠券使用门槛"));

        // 5. 满 36 元下单用券 → 应付 36-5=31 元，订单含券快照
        mockMvc.perform(post("/api/v1/me/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":3}],\"couponId\":" + userCouponId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(3600))
                .andExpect(jsonPath("$.data.couponDiscount").value(500))
                .andExpect(jsonPath("$.data.couponNameSnapshot").value("满30减5咖啡券"))
                .andExpect(jsonPath("$.data.payableAmount").value(3100))
                .andExpect(jsonPath("$.data.discountAmount").value(500));

        // 6. 券已核销（status=1，绑定订单）
        mockMvc.perform(get("/api/v1/me/coupons").param("status", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].orderId").isString()); // 雪花 ID 以字符串下发，防前端精度截断
    }

    @Test
    void expiredCoupon_shouldReject() throws Exception {
        String token = wxAccessToken();
        // 造一张已过期的用户券
        MktUserCoupon expired = new MktUserCoupon();
        expired.setCouponId(1L);
        expired.setUserId(objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/me")
                                        .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asLong());
        expired.setCouponName("满30减5咖啡券");
        expired.setCouponType(1);
        expired.setDiscountAmount(500L);
        expired.setThresholdAmount(0L);
        expired.setBizType(1);
        expired.setStatus(0);
        expired.setExpireAt(LocalDateTime.now().minusMinutes(5));
        userCouponMapper.insert(expired);

        mockMvc.perform(post("/api/v1/me/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":3}],\"couponId\":" + expired.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("优惠券已过期"));
    }
}
