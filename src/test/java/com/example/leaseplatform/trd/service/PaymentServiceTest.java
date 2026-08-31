package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.leaseplatform.trd.entity.TrdPayment;
import com.example.leaseplatform.trd.mapper.TrdPaymentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付服务单元测试（1.2）：创建支付单 / 结算幂等（status 0→1 乐观更新）。
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private TrdPaymentMapper paymentMapper;

    private PaymentService service;

    @BeforeAll
    static void initMpEntityCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TrdPayment.class);
    }

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentMapper);
    }

    @Test
    void create_shouldInsertPendingPayment() {
        when(paymentMapper.insert(any(TrdPayment.class))).thenAnswer(inv -> {
            ((TrdPayment) inv.getArgument(0)).setId(800L);
            return 1;
        });

        TrdPayment payment = service.create(1L, PaymentService.BIZ_ORDER, 100L, 2400L,
                PaymentService.METHOD_BALANCE, PaymentService.CHANNEL_BALANCE, "PO123");

        assertThat(payment.getId()).isEqualTo(800L);
        assertThat(payment.getPaymentNo()).startsWith("PAY");
        assertThat(payment.getStatus()).isEqualTo(PaymentService.STATUS_PENDING);
        assertThat(payment.getAmount()).isEqualTo(2400L);
        assertThat(payment.getOutTradeNo()).isEqualTo("PO123");
        verify(paymentMapper).insert(any(TrdPayment.class));
    }

    @Test
    void settle_shouldFlipPendingToSuccessOnce() {
        when(paymentMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        assertThat(service.settle(800L, "WX001")).isTrue();
        // 第二次（并发/重复回调）乐观更新 0 行 → 不再入账
        when(paymentMapper.update(any(), any(Wrapper.class))).thenReturn(0);
        assertThat(service.settle(800L, "WX001")).isFalse();
    }

    @Test
    void getByOutTradeNo_shouldQueryByMerchantNo() {
        TrdPayment payment = new TrdPayment();
        payment.setId(800L);
        when(paymentMapper.selectOne(any(Wrapper.class))).thenReturn(payment);

        assertThat(service.getByOutTradeNo("RC123").getId()).isEqualTo(800L);
        verify(paymentMapper).selectOne(any(Wrapper.class));
    }

    @Test
    void settleByOutTradeNo_missingPayment_shouldSkip() {
        when(paymentMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThat(service.settleByOutTradeNo("RC_LEGACY", "WX999")).isFalse();
    }

    @Test
    void settleByOutTradeNo_shouldSettle() {
        TrdPayment payment = new TrdPayment();
        payment.setId(800L);
        when(paymentMapper.selectOne(any(Wrapper.class))).thenReturn(payment);
        when(paymentMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        assertThat(service.settleByOutTradeNo("RC123", "WX001")).isTrue();
        verify(paymentMapper).update(any(), any(Wrapper.class));
    }
}
