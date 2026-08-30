package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.trd.dto.RechargeTierReq;
import com.example.leaseplatform.trd.dto.RechargeTierVO;
import com.example.leaseplatform.trd.entity.TrdRechargeTier;
import com.example.leaseplatform.trd.mapper.TrdRechargeTierMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 充值档位服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RechargeTierServiceTest {

    @Mock
    private TrdRechargeTierMapper tierMapper;

    private RechargeTierService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // 须在 @Mock 注入后构造（字段初始化器在注入前执行会拿到 null mock）
        service = new RechargeTierService(tierMapper);
    }

    private RechargeTierReq req(BigDecimal amount, BigDecimal bonus) {
        RechargeTierReq req = new RechargeTierReq();
        req.setRechargeAmount(amount);
        req.setBonusAmount(bonus);
        req.setEquivalentDiscount(new BigDecimal("0.91"));
        return req;
    }

    private TrdRechargeTier tier(Long id) {
        TrdRechargeTier t = new TrdRechargeTier();
        t.setId(id);
        t.setRechargeAmount(new BigDecimal("200.00"));
        t.setBonusAmount(new BigDecimal("20.00"));
        t.setActualAmount(new BigDecimal("220.00"));
        t.setStatus(1);
        return t;
    }

    @Test
    void publicList_shouldReturnEnabledOnly() {
        when(tierMapper.selectList(any(Wrapper.class))).thenReturn(List.of(tier(1L)));

        List<RechargeTierVO> list = service.publicList();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getActualAmount()).isEqualByComparingTo("220.00");
    }

    @Test
    void create_shouldComputeActualAmount() {
        when(tierMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(tierMapper.insert(any(TrdRechargeTier.class))).thenAnswer(inv -> {
            ((TrdRechargeTier) inv.getArgument(0)).setId(1L);
            return 1;
        });

        RechargeTierVO vo = service.create(req(new BigDecimal("200.00"), new BigDecimal("20.00")));

        // 实际到账 = 充值 + 赠送
        assertThat(vo.getActualAmount()).isEqualByComparingTo("220.00");
    }

    @Test
    void create_duplicateAmount_shouldConflict() {
        when(tierMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.create(req(new BigDecimal("200.00"), new BigDecimal("20.00"))))
                .isInstanceOf(BizException.class)
                .hasMessage("该充值金额档位已存在");
        verify(tierMapper, never()).insert(any(TrdRechargeTier.class));
    }

    @Test
    void page_shouldReturnPaged() {
        when(tierMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<TrdRechargeTier> p = inv.getArgument(0);
            p.setRecords(List.of(tier(1L)));
            p.setTotal(1);
            return p;
        });

        PageResult<RechargeTierVO> result = service.page(1, 10, null);

        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void delete_missing_should404() {
        when(tierMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BizException.class)
                .hasMessage("充值档位不存在");
    }
}
