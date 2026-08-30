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

import java.time.LocalDate;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 正餐预订端到端集成测试（真实 MySQL + Redis + 完整 Security 链）：
 * 菜单整单配置/复制 → 预订（规则校验）→ 余额支付（快照）→ 商家备餐流转 → 取消退款。
 * 依赖：先执行 ./reset_db.sh（套餐种子 product_id=4 等）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrdMealReservationIntegrationTest {

    /** 可订窗口内日期（今天+3，恒合法） */
    private final LocalDate bookingDate = LocalDate.now().plusDays(3);

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

    /** 为预订日期整单配置菜单（3荤1素套餐 productId=4） */
    private void setupMenu(String adminToken) throws Exception {
        mockMvc.perform(post("/api/v1/menus/batch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"menuDate":"%s","items":[
                                  {"productId":4,"dishName":"红烧肉","dishType":1,"sortOrder":1},
                                  {"productId":4,"dishName":"清炒时蔬","dishType":2,"sortOrder":2},
                                  {"productId":4,"dishName":"紫菜蛋花汤","dishType":3,"sortOrder":3}]}"""
                                .formatted(bookingDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    private void recharge(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/me/recharge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tierId\":2}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long recordId = objectMapper.readTree(body).path("data").path("record").path("id").asLong();
        mockMvc.perform(post("/api/v1/me/recharge/" + recordId + "/mock-pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void mealReservationFullFlow_shouldWork() throws Exception {
        String adminToken = adminAccessToken();
        setupMenu(adminToken);
        String token = wxAccessToken();
        recharge(token); // 折扣率 0.89

        // 1. 规则校验：今天不可订
        mockMvc.perform(post("/api/v1/me/meal-reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":4,"menuDate":"%s","timeSlot":"午餐","quantity":2,"deliveryType":1}"""
                                .formatted(LocalDate.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("可预订日期")));

        // 2. 预订：套餐 20×2=40，0.89 折 → 35.60；自取配送费 0
        String reserveBody = mockMvc.perform(post("/api/v1/me/meal-reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":4,"menuDate":"%s","timeSlot":"午餐","quantity":2,"deliveryType":1,
                                 "remark":"少辣"}"""
                                .formatted(bookingDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(40.0))
                .andExpect(jsonPath("$.data.payableAmount").value(35.60))
                .andExpect(jsonPath("$.data.deliveryFee").value(0.0))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.items[0].dishDetails.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        long reservationId = objectMapper.readTree(reserveBody).path("data").path("id").asLong();

        // 3. 余额支付 → 待备餐
        mockMvc.perform(post("/api/v1/me/meal-reservations/" + reservationId + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        // 4. 我的预订 + 详情（菜品快照）
        mockMvc.perform(get("/api/v1/me/meal-reservations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/me/meal-reservations/" + reservationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].dishDetails[0].dishName").value("红烧肉"));

        // 5. 商家：备餐流转 1→2→3
        mockMvc.perform(get("/api/v1/meal-reservations").param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(put("/api/v1/meal-reservations/" + reservationId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderStatus\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));
        mockMvc.perform(put("/api/v1/meal-reservations/" + reservationId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderStatus\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(3));
    }

    @Test
    void menuCopyAndCancelRefund_shouldWork() throws Exception {
        String adminToken = adminAccessToken();
        setupMenu(adminToken);

        // 复制菜单到另一窗口日期
        LocalDate otherDate = bookingDate.plusDays(0); // 同窗口另一天
        if (otherDate.equals(bookingDate)) {
            otherDate = LocalDate.now().plusDays(2);
        }
        mockMvc.perform(post("/api/v1/menus/copy")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceDate\":\"%s\",\"targetDate\":\"%s\"}"
                                .formatted(bookingDate, otherDate)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/menus").param("date", otherDate.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        // 预订（在复制日期的菜单上）→ 支付 → 取消退款
        String token = wxAccessToken();
        recharge(token);
        String body = mockMvc.perform(post("/api/v1/me/meal-reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":4,"menuDate":"%s","timeSlot":"晚餐","quantity":1,"deliveryType":3,
                                 "deliveryAddress":"3号楼501"}"""
                                .formatted(otherDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryFee").value(5.0)) // 周边配送费 5 元
                .andReturn().getResponse().getContentAsString();
        long reservationId = objectMapper.readTree(body).path("data").path("id").asLong();
        mockMvc.perform(post("/api/v1/me/meal-reservations/" + reservationId + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/meal-reservations/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"行程变化\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(4));

        // 按日期清空菜单
        mockMvc.perform(delete("/api/v1/menus").param("date", otherDate.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/menus").param("date", otherDate.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
