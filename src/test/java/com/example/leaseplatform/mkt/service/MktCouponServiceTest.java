package com.example.leaseplatform.mkt.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.mkt.dto.CouponCreateReq;
import com.example.leaseplatform.mkt.dto.CouponVO;
import com.example.leaseplatform.mkt.entity.MktCoupon;
import com.example.leaseplatform.mkt.entity.MktUserCoupon;
import com.example.leaseplatform.mkt.mapper.MktCouponMapper;
import com.example.leaseplatform.mkt.mapper.MktUserCouponMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 优惠券模板服务单元测试（1.6）：CRUD / 公开列表 / 删除引用保护。
 */
@ExtendWith(MockitoExtension.class)
class MktCouponServiceTest {

    @Mock
    private MktCouponMapper couponMapper;
    @Mock
    private MktUserCouponMapper userCouponMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MktCouponService service;

    @BeforeAll
    static void initMpEntityCache() {
        initTableInfo(MktCoupon.class);
        initTableInfo(MktUserCoupon.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @BeforeEach
    void setUp() {
        service = new MktCouponService(couponMapper, userCouponMapper, objectMapper);
    }

    private MktCoupon coupon(Long id, String name) {
        MktCoupon c = new MktCoupon();
        c.setId(id);
        c.setCouponName(name);
        c.setCouponType(MktCoupon.TYPE_FULL_REDUCTION);
        c.setDiscountAmount(500L);
        c.setThresholdAmount(3000L);
        c.setBizType(1);
        c.setValidityDays(30);
        c.setStatus(MktCoupon.STATUS_ENABLED);
        return c;
    }

    @Test
    void create_fullReduction_shouldInsert() {
        when(couponMapper.insert(any(MktCoupon.class))).thenAnswer(inv -> {
            ((MktCoupon) inv.getArgument(0)).setId(10L);
            return 1;
        });
        CouponCreateReq req = new CouponCreateReq();
        req.setCouponName("满30减5咖啡券");
        req.setCouponType(MktCoupon.TYPE_FULL_REDUCTION);
        req.setDiscountAmount(500L);
        req.setThresholdAmount(3000L);
        req.setBizType(1);

        CouponVO vo = service.create(req);

        assertThat(vo.getId()).isEqualTo(10L);
        assertThat(vo.getDiscountAmount()).isEqualTo(500L);
        assertThat(vo.getBizType()).isEqualTo(1);
    }

    @Test
    void create_discount_shouldStoreRate() {
        when(couponMapper.insert(any(MktCoupon.class))).thenReturn(1);
        CouponCreateReq req = new CouponCreateReq();
        req.setCouponName("正餐9折券");
        req.setCouponType(MktCoupon.TYPE_DISCOUNT);
        req.setDiscountRate(new BigDecimal("0.90"));

        service.create(req);

        verify(couponMapper).insert(any(MktCoupon.class));
    }

    @Test
    void delete_referencedByUsers_shouldConflict() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(1L, "满30减5"));
        when(userCouponMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已被用户领取");
        verify(couponMapper, never()).deleteById(org.mockito.ArgumentMatchers.<Long>any());
    }

    @Test
    void delete_notReferenced_shouldDelete() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(1L, "满30减5"));
        when(userCouponMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.delete(1L);

        verify(couponMapper).deleteById(1L);
    }

    @Test
    void publicList_shouldReturnEnabledOnly() {
        when(couponMapper.selectList(any(Wrapper.class))).thenReturn(List.of(coupon(1L, "满30减5")));

        assertThat(service.publicList()).hasSize(1);
    }

    @Test
    void page_shouldReturnPaged() {
        when(couponMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<MktCoupon> p = inv.getArgument(0);
            p.setRecords(List.of(coupon(1L, "满30减5")));
            p.setTotal(1);
            return p;
        });

        PageResult<CouponVO> result = service.page(1, 10, null, null, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getCouponName()).isEqualTo("满30减5");
    }
}
