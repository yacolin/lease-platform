package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.trd.entity.TrdPayment;
import com.example.leaseplatform.trd.entity.TrdRefund;
import com.example.leaseplatform.trd.mapper.TrdPaymentMapper;
import com.example.leaseplatform.trd.mapper.TrdRefundMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款服务单元测试（1.2）：全额/部分退款、可退金额校验、幂等键防重复、支付单状态联动。
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private TrdRefundMapper refundMapper;
    @Mock
    private TrdPaymentMapper paymentMapper;
    @Mock
    private AccountService accountService;

    private RefundService service;

    @BeforeAll
    static void initMpEntityCache() {
        initTableInfo(TrdRefund.class);
        initTableInfo(TrdPayment.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @BeforeEach
    void setUp() {
        service = new RefundService(refundMapper, paymentMapper, accountService);
    }

    private TrdPayment paidPayment(Long id, long amount) {
        TrdPayment p = new TrdPayment();
        p.setId(id);
        p.setUserId(1L);
        p.setBizType(PaymentService.BIZ_ORDER);
        p.setBizId(100L);
        p.setAmount(amount);
        p.setStatus(PaymentService.STATUS_SUCCESS);
        return p;
    }

    @Test
    void refundToBalance_partial_shouldCreditAndMarkPartialRefunded() {
        when(paymentMapper.selectById(900L)).thenReturn(paidPayment(900L, 2400L));
        when(refundMapper.selectList(any(Wrapper.class))).thenReturn(List.of()); // 已退 0
        when(refundMapper.insert(any(TrdRefund.class))).thenReturn(1);
        when(paymentMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        TrdRefund refund = service.refundToBalance(1L, 900L, 1000L,
                "部分退款", null, 100L);

        assertThat(refund.getRefundAmount()).isEqualTo(1000L);
        assertThat(refund.getStatus()).isEqualTo(RefundService.STATUS_SUCCESS);
        assertThat(refund.getRefundNo()).startsWith("RF");
        verify(accountService).credit(1L, 1000L, 0L, AccountService.TX_REFUND, 100L, null, "部分退款");
        assertPaymentStatus(3); // 部分退款
    }

    @Test
    void refundToBalance_full_shouldMarkRefunded() {
        when(paymentMapper.selectById(900L)).thenReturn(paidPayment(900L, 2400L));
        when(refundMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(refundMapper.insert(any(TrdRefund.class))).thenReturn(1);
        when(paymentMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        service.refundToBalance(1L, 900L, 2400L, "全额退款", null, 100L);

        assertPaymentStatus(4); // 已退款
    }

    @Test
    void refundToBalance_exceedRemaining_should400() {
        TrdPayment payment = paidPayment(900L, 2400L);
        when(paymentMapper.selectById(900L)).thenReturn(payment);
        // 已退 2000，剩余 400
        TrdRefund done = new TrdRefund();
        done.setRefundAmount(2000L);
        done.setStatus(RefundService.STATUS_SUCCESS);
        when(refundMapper.selectList(any(Wrapper.class))).thenReturn(List.of(done));

        assertThatThrownBy(() -> service.refundToBalance(1L, 900L, 1000L, "超额", null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("退款金额超出可退金额");
        verify(accountService, never()).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void refundToBalance_notPaid_shouldConflict() {
        TrdPayment payment = paidPayment(900L, 2400L);
        payment.setStatus(PaymentService.STATUS_PENDING);
        when(paymentMapper.selectById(900L)).thenReturn(payment);

        assertThatThrownBy(() -> service.refundToBalance(1L, 900L, 1000L, "未支付", null, null))
                .isInstanceOf(BizException.class)
                .hasMessage("支付单不可退款");
    }

    @Test
    void refundToBalance_paymentNotFound_should404() {
        when(paymentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.refundToBalance(1L, 999L, 100L, "无支付单", null, null))
                .isInstanceOf(BizException.class)
                .hasMessage("支付单不存在");
    }

    @Test
    void refundToBalance_sameIdempotencyKey_shouldReturnExisting() {
        TrdRefund existing = new TrdRefund();
        existing.setId(50L);
        existing.setRefundNo("RF001");
        existing.setRefundAmount(1000L);
        when(refundMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        TrdRefund result = service.refundToBalance(1L, 900L, 1000L, "重复", "ORDER_CANCEL_REFUND:100", 100L);

        assertThat(result.getId()).isEqualTo(50L);
        verify(accountService, never()).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
        verify(refundMapper, never()).insert(any(TrdRefund.class));
    }

    @Test
    void refundedAmount_shouldSumSuccessOnly() {
        // 服务信任 Mapper 的 status 过滤：模拟只返回成功退款
        TrdRefund ok = new TrdRefund();
        ok.setRefundAmount(1000L);
        ok.setStatus(RefundService.STATUS_SUCCESS);
        when(refundMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ok));

        assertThat(service.refundedAmount(900L)).isEqualTo(1000L);
    }

    /** 断言支付单状态被更新为期望值（3-部分退款 / 4-已退款） */
    @SuppressWarnings("unchecked")
    private void assertPaymentStatus(int expectedStatus) {
        ArgumentCaptor<Wrapper<TrdPayment>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(paymentMapper).update(any(), captor.capture());
        Map<String, Object> params = ((AbstractWrapper<TrdPayment, ?, ?>) captor.getValue())
                .getParamNameValuePairs();
        assertThat(params).containsValue(expectedStatus);
    }
}
