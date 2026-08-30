package com.example.leaseplatform.mtg;

import com.example.leaseplatform.mtg.entity.MtgReservation;
import com.example.leaseplatform.mtg.mapper.MtgReservationMapper;
import com.example.leaseplatform.usr.entity.UsrEnterprise;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrEnterpriseMapper;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会议室域端到端集成测试（真实 MySQL + Redis + 完整 Security 链）：
 * 公开会议室 → 付费预约（冲突 409）→ 支付 → 取消退款；
 * 企业会员免费时长抵扣 → 超时计费 → 商家完成 → 惰性过期。
 * 依赖：先执行 ./reset_db.sh（mtg_rooms 种子 3 间、usr_member_levels VIP=4h/月）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MtgMeetingReservationIntegrationTest {

    private final LocalDate bookingDate = LocalDate.now().plusDays(2);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsrUserMapper userMapper;
    @Autowired
    private UsrEnterpriseMapper enterpriseMapper;
    @Autowired
    private MtgReservationMapper reservationMapper;
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

    private String reserveBody(LocalTime start, LocalTime end) {
        return """
                {"roomId":1,"reservationDate":"%s","startTime":"%s","endTime":"%s","meetingTopic":"周会"}"""
                .formatted(bookingDate, start, end);
    }

    @Test
    void paidReservation_flow_shouldWork() throws Exception {
        String token = wxAccessToken();
        recharge(token); // 余额 500 + 赠送 60

        // 1. 公开会议室列表（seed.py 8 间；超出费用已不在会议室上，按等级/覆盖价解析）
        mockMvc.perform(get("/api/v1/public/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8));

        // 2. 个人用户预约 2h（9:00-11:00）→ 无免费时长 → BASIC 兜底价 80 × 2 = 160 元待确认
        String body = mockMvc.perform(post("/api/v1/me/meeting-reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(LocalTime.of(9, 0), LocalTime.of(11, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(16000))
                .andExpect(jsonPath("$.data.overtimeUnitPrice").value(8000))   // 价格快照：单价
                .andExpect(jsonPath("$.data.freeHoursDeducted").value(0.0))     // 价格快照：无抵扣
                .andExpect(jsonPath("$.data.isFree").value(0))
                .andExpect(jsonPath("$.data.status").value(0))
                .andReturn().getResponse().getContentAsString();
        long reservationId = objectMapper.readTree(body).path("data").path("id").asLong();

        // 3. 时段冲突：10:00-12:00 重叠 → 409
        mockMvc.perform(post("/api/v1/me/meeting-reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(LocalTime.of(10, 0), LocalTime.of(12, 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("该时段已被预约"));

        // 4. 支付 → 已确认（debit 赠送优先：赠送 60 + 余额 100 → 余额 500-100=400）
        mockMvc.perform(post("/api/v1/me/meeting-reservations/" + reservationId + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(40000))
                .andExpect(jsonPath("$.data.giftBalance").value(0));

        // 5. 取消 → 退款入余额（400+160=560）
        mockMvc.perform(post("/api/v1/me/meeting-reservations/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"改期\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(3));
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(56000));
    }

    @Test
    void memberFreeHours_flow_shouldWork() throws Exception {
        // 企业 VIP 会员：4h/月免费时长
        String token = wxAccessToken();
        UsrUser user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UsrUser>()
                .eq(UsrUser::getOpenid, "mock_dev_user"));
        UsrEnterprise enterprise = new UsrEnterprise();
        enterprise.setEnterpriseName("测试企业");
        enterprise.setUnifiedSocialCreditCode("MTG-TEST");
        enterprise.setBusinessLicenseUrl("https://x/license.png");
        enterprise.setLegalPersonName("张三");
        enterprise.setLegalPersonIdCardFront("https://x/f.png");
        enterprise.setLegalPersonIdCardBack("https://x/b.png");
        enterprise.setContactName("张三");
        enterprise.setContactPhone("13800138000");
        enterprise.setMemberLevel(2); // VIP
        enterprise.setMemberExpireAt(LocalDateTime.now().plusDays(30));
        enterprise.setAuditStatus(1);
        enterprise.setStatus(1);
        enterpriseMapper.insert(enterprise);
        user.setEnterpriseId(enterprise.getId());
        user.setMemberLevel(2);
        userMapper.updateById(user);
        recharge(token); // 超时计费单需余额支付

        // 1. 免费预约 2h（8:00-10:00）→ is_free=1、fee=0、直接已确认，快照记录抵扣 2h
        String r1 = mockMvc.perform(post("/api/v1/me/meeting-reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(LocalTime.of(8, 0), LocalTime.of(10, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFree").value(1))
                .andExpect(jsonPath("$.data.feeAmount").value(0))
                .andExpect(jsonPath("$.data.freeHoursDeducted").value(2.0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andReturn().getResponse().getContentAsString();
        long r1Id = objectMapper.readTree(r1).path("data").path("id").asLong();

        // 2. 再约 2h（10:00-12:00）→ 仍免费（4h 内），剩余 0
        mockMvc.perform(post("/api/v1/me/meeting-reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(LocalTime.of(10, 0), LocalTime.of(12, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFree").value(1));

        // 3. 免费时长查询（按预约月，月末跨月也正确）：剩余 0
        mockMvc.perform(get("/api/v1/me/meeting-reservations/free-hours")
                        .header("Authorization", "Bearer " + token)
                        .param("month", bookingDate.getYear() + "-"
                                + String.format("%02d", bookingDate.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalHours").value(4.0))
                .andExpect(jsonPath("$.data.remainingHours").value(0.0));

        // 4. 超时计费：再约 2h（12:00-14:00）→ 超出 2h × VIP 默认价 80 = 160，待支付
        //    （会议室 1 未配置覆盖价 → 回落 usr_member_levels.meeting_overtime_fee）
        mockMvc.perform(post("/api/v1/me/meeting-reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(LocalTime.of(12, 0), LocalTime.of(14, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(16000))
                .andExpect(jsonPath("$.data.overtimeUnitPrice").value(8000))
                .andExpect(jsonPath("$.data.isFree").value(0))
                .andExpect(jsonPath("$.data.status").value(0));

        // 5. 商家完成第一个预约
        String adminToken = adminAccessToken();
        mockMvc.perform(get("/api/v1/meeting-reservations").param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
        mockMvc.perform(put("/api/v1/meeting-reservations/" + r1Id + "/complete")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));
    }

    @Test
    void pastReservation_shouldExpireOnQuery() throws Exception {
        String token = wxAccessToken();
        // 造一条昨天日期的已确认预约
        MtgReservation past = new MtgReservation();
        past.setReservationNo("MR-PAST");
        past.setRoomId(1L);
        past.setUserId(userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UsrUser>()
                .eq(UsrUser::getOpenid, "mock_dev_user")).getId());
        past.setEnterpriseId(0L);
        past.setReservationDate(LocalDate.now().minusDays(1));
        past.setStartTime(LocalTime.of(9, 0));
        past.setEndTime(LocalTime.of(10, 0));
        past.setDurationHours(new BigDecimal("1.0"));
        past.setMeetingTopic("过期会议");
        past.setStatus(1);
        past.setFeeAmount(0L);
        past.setIsFree(1);
        past.setOvertimeUnitPrice(8000L);
        past.setFreeHoursDeducted(new BigDecimal("1.0"));
        reservationMapper.insert(past);

        // 查询时惰性置为已过期
        mockMvc.perform(get("/api/v1/me/meeting-reservations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].status").value(4));
        assertThat(reservationMapper.selectById(past.getId()).getStatus()).isEqualTo(4);
    }

    private String adminAccessToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }
}
