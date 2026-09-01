package com.example.leaseplatform.mkt.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.mkt.dto.UserCouponVO;
import com.example.leaseplatform.mkt.entity.MktCoupon;
import com.example.leaseplatform.mkt.entity.MktUserCoupon;
import com.example.leaseplatform.mkt.mapper.MktUserCouponMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户优惠券服务单元测试（1.6）：领取（快照）/重复领取/惰性过期/下单校验（业务/门槛/范围）/
 * 优惠计算（满减/折扣）/标记使用。
 */
@ExtendWith(MockitoExtension.class)
class MktUserCouponServiceTest {

    @Mock
    private MktUserCouponMapper userCouponMapper;
    @Mock
    private MktCouponService couponService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MktUserCouponService service;

    @BeforeAll
    static void initMpEntityCache() {
        initTableInfo(MktUserCoupon.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @BeforeEach
    void setUp() {
        service = new MktUserCouponService(userCouponMapper, couponService);
    }

    private MktCoupon coupon(int type) {
        MktCoupon c = new MktCoupon();
        c.setId(1L);
        c.setCouponName("测试券");
        c.setCouponType(type);
        c.setDiscountAmount(500L);
        c.setDiscountRate(new BigDecimal("0.90"));
        c.setThresholdAmount(3000L);
        c.setBizType(MktUserCouponService.BIZ_COFFEE);
        c.setValidityDays(30);
        c.setStatus(MktCoupon.STATUS_ENABLED);
        return c;
    }

    private MktUserCoupon userCoupon(int status, long expireAtOffsetMinutes, Integer bizType,
                                     Long threshold, Integer type) {
        MktUserCoupon uc = new MktUserCoupon();
        uc.setId(10L);
        uc.setCouponId(1L);
        uc.setUserId(1L);
        uc.setCouponName("测试券");
        uc.setCouponType(type);
        uc.setDiscountAmount(500L);
        uc.setDiscountRate(new BigDecimal("0.90"));
        uc.setThresholdAmount(threshold);
        uc.setBizType(bizType);
        uc.setStatus(status);
        uc.setExpireAt(LocalDateTime.now().plusMinutes(expireAtOffsetMinutes));
        return uc;
    }

    @Test
    void claim_shouldSnapshotCouponRule() {
        when(couponService.require(1L)).thenReturn(coupon(MktCoupon.TYPE_FULL_REDUCTION));
        when(userCouponMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(userCouponMapper.insert(any(MktUserCoupon.class))).thenReturn(1);

        UserCouponVO vo = service.claim(1L, 1L);

        ArgumentCaptor<MktUserCoupon> captor = ArgumentCaptor.forClass(MktUserCoupon.class);
        verify(userCouponMapper).insert(captor.capture());
        // 快照：券名/类型/金额/门槛/业务
        assertThat(captor.getValue().getCouponName()).isEqualTo("测试券");
        assertThat(captor.getValue().getCouponType()).isEqualTo(MktCoupon.TYPE_FULL_REDUCTION);
        assertThat(captor.getValue().getDiscountAmount()).isEqualTo(500L);
        assertThat(captor.getValue().getThresholdAmount()).isEqualTo(3000L);
        assertThat(captor.getValue().getStatus()).isEqualTo(MktUserCoupon.STATUS_UNUSED);
        assertThat(captor.getValue().getExpireAt()).isAfter(LocalDateTime.now());
        assertThat(vo.getExpireAt()).isNotNull();
    }

    @Test
    void claim_duplicate_shouldConflict() {
        when(couponService.require(1L)).thenReturn(coupon(MktCoupon.TYPE_FULL_REDUCTION));
        when(userCouponMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.claim(1L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("已领取过该优惠券");
        verify(userCouponMapper, never()).insert(any(MktUserCoupon.class));
    }

    @Test
    void claim_disabled_should400() {
        MktCoupon c = coupon(MktCoupon.TYPE_FULL_REDUCTION);
        c.setStatus(MktCoupon.STATUS_DISABLED);
        when(couponService.require(1L)).thenReturn(c);

        assertThatThrownBy(() -> service.claim(1L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("该优惠券已停用");
    }

    @Test
    void apply_fullReduction_shouldReturnMinAmount() {
        when(userCouponMapper.selectById(10L))
                .thenReturn(userCoupon(MktUserCoupon.STATUS_UNUSED, 60L, MktUserCouponService.BIZ_COFFEE, 3000L,
                        MktCoupon.TYPE_FULL_REDUCTION));

        var result = service.apply(1L, 10L, MktUserCouponService.BIZ_COFFEE, 3500L, List.of(1L), List.of(1L));

        assertThat(result.discount()).isEqualTo(500L); // 满 35 减 5
        assertThat(result.userCoupon().getId()).isEqualTo(10L);
    }

    @Test
    void apply_discount_shouldReturnRateOff() {
        when(userCouponMapper.selectById(10L))
                .thenReturn(userCoupon(MktUserCoupon.STATUS_UNUSED, 60L, MktUserCouponService.BIZ_COFFEE, 0L,
                        MktCoupon.TYPE_DISCOUNT));

        var result = service.apply(1L, 10L, MktUserCouponService.BIZ_COFFEE, 4000L, List.of(1L), List.of(1L));

        assertThat(result.discount()).isEqualTo(400L); // 40 × 0.10
    }

    @Test
    void apply_wrongBiz_should400() {
        when(userCouponMapper.selectById(10L))
                .thenReturn(userCoupon(MktUserCoupon.STATUS_UNUSED, 60L, MktUserCouponService.BIZ_COFFEE, 0L,
                        MktCoupon.TYPE_DISCOUNT));

        assertThatThrownBy(() -> service.apply(1L, 10L, MktUserCouponService.BIZ_MEAL, 4000L, List.of(1L), List.of(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不适用于此订单");
    }

    @Test
    void apply_thresholdNotMet_should400() {
        when(userCouponMapper.selectById(10L))
                .thenReturn(userCoupon(MktUserCoupon.STATUS_UNUSED, 60L, MktUserCouponService.BIZ_COFFEE, 3000L,
                        MktCoupon.TYPE_FULL_REDUCTION));

        assertThatThrownBy(() -> service.apply(1L, 10L, MktUserCouponService.BIZ_COFFEE, 2000L, List.of(1L), List.of(1L)))
                .isInstanceOf(BizException.class)
                .hasMessage("未满足优惠券使用门槛");
    }

    @Test
    void apply_expired_should400AndMark() {
        when(userCouponMapper.selectById(10L))
                .thenReturn(userCoupon(MktUserCoupon.STATUS_UNUSED, -10L, MktUserCouponService.BIZ_COFFEE, 0L,
                        MktCoupon.TYPE_DISCOUNT));

        assertThatThrownBy(() -> service.apply(1L, 10L, MktUserCouponService.BIZ_COFFEE, 4000L, List.of(1L), List.of(1L)))
                .isInstanceOf(BizException.class)
                .hasMessage("优惠券已过期");
        verify(userCouponMapper).update(any(), any(Wrapper.class)); // 置过期
    }

    @Test
    void apply_productScopeNotMatch_should400() {
        MktUserCoupon uc = userCoupon(MktUserCoupon.STATUS_UNUSED, 60L, MktUserCouponService.BIZ_COFFEE, 0L,
                MktCoupon.TYPE_FULL_REDUCTION);
        uc.setProductIds("[1,2]");
        when(userCouponMapper.selectById(10L)).thenReturn(uc);

        assertThatThrownBy(() -> service.apply(1L, 10L, MktUserCouponService.BIZ_COFFEE, 4000L,
                List.of(9L), List.of(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不适用于所选商品");
    }

    @Test
    void use_shouldMarkUsedOnce() {
        when(userCouponMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        assertThat(service.use(10L, 200L)).isTrue();
    }

    @Test
    void myCoupons_shouldExpireAndReturnPaged() {
        when(userCouponMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<MktUserCoupon> p = inv.getArgument(0);
            p.setRecords(List.of(userCoupon(MktUserCoupon.STATUS_UNUSED, 60L, null, 0L,
                    MktCoupon.TYPE_FULL_REDUCTION)));
            p.setTotal(1);
            return p;
        });

        PageResult<UserCouponVO> result = service.myCoupons(1L, 1, 10, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getStatus()).isZero();
        verify(userCouponMapper).update(any(), any(Wrapper.class)); // 惰性过期
    }
}
