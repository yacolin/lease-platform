package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.trd.dto.BalanceTransactionVO;
import com.example.leaseplatform.trd.entity.TrdBalanceTransaction;
import com.example.leaseplatform.trd.mapper.TrdBalanceTransactionMapper;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * 余额账本单元测试：入账/出账/流水。
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private UsrUserMapper userMapper;
    @Mock
    private TrdBalanceTransactionMapper txMapper;

    private BalanceService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // 须在 @Mock 注入后构造（字段初始化器在注入前执行会拿到 null mock）
        service = new BalanceService(userMapper, txMapper);
    }

    private UsrUser user(BigDecimal balance, BigDecimal gift) {
        UsrUser u = new UsrUser();
        u.setId(1L);
        u.setBalance(balance);
        u.setGiftBalance(gift);
        return u;
    }

    @Test
    void credit_shouldAddBalanceAndGiftAndWriteTx() {
        when(userMapper.selectById(1L)).thenReturn(user(new BigDecimal("100.00"), new BigDecimal("10.00")));

        service.credit(1L, new BigDecimal("200.00"), new BigDecimal("20.00"),
                BalanceService.TX_RECHARGE, null, 9L, "余额充值");

        ArgumentCaptor<UsrUser> userCaptor = ArgumentCaptor.forClass(UsrUser.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertThat(userCaptor.getValue().getBalance()).isEqualByComparingTo("300.00");
        assertThat(userCaptor.getValue().getGiftBalance()).isEqualByComparingTo("30.00");

        ArgumentCaptor<TrdBalanceTransaction> txCaptor = ArgumentCaptor.forClass(TrdBalanceTransaction.class);
        verify(txMapper).insert(txCaptor.capture());
        TrdBalanceTransaction tx = txCaptor.getValue();
        assertThat(tx.getTransactionType()).isEqualTo(BalanceService.TX_RECHARGE);
        assertThat(tx.getAmount()).isEqualByComparingTo("220.00"); // 充值 + 赠送
        assertThat(tx.getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(tx.getGiftBalanceBefore()).isEqualByComparingTo("10.00");
        assertThat(tx.getRelatedRechargeId()).isEqualTo(9L);
    }

    @Test
    void debit_shouldDeductGiftFirstThenBalance() {
        // 余额 100、赠送 30，消费 50 → 赠送扣 30、余额扣 20
        when(userMapper.selectById(1L)).thenReturn(user(new BigDecimal("100.00"), new BigDecimal("30.00")));

        service.debit(1L, new BigDecimal("50.00"), 7L, "咖啡订单");

        ArgumentCaptor<UsrUser> userCaptor = ArgumentCaptor.forClass(UsrUser.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertThat(userCaptor.getValue().getBalance()).isEqualByComparingTo("80.00");
        assertThat(userCaptor.getValue().getGiftBalance()).isEqualByComparingTo("0.00");

        ArgumentCaptor<TrdBalanceTransaction> txCaptor = ArgumentCaptor.forClass(TrdBalanceTransaction.class);
        verify(txMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("-50.00");
        assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(BalanceService.TX_CONSUME);
        assertThat(txCaptor.getValue().getRelatedOrderId()).isEqualTo(7L);
    }

    @Test
    void debit_insufficient_shouldConflict() {
        when(userMapper.selectById(1L)).thenReturn(user(new BigDecimal("10.00"), new BigDecimal("0.00")));

        assertThatThrownBy(() -> service.debit(1L, new BigDecimal("50.00"), 7L, "咖啡订单"))
                .isInstanceOf(BizException.class)
                .hasMessage("余额不足");
        verify(txMapper, never()).insert(any(TrdBalanceTransaction.class));
    }

    @Test
    void myTransactions_shouldReturnPaged() {
        when(txMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<TrdBalanceTransaction> p = inv.getArgument(0);
            TrdBalanceTransaction tx = new TrdBalanceTransaction();
            tx.setId(1L);
            tx.setTransactionType(1);
            tx.setAmount(new BigDecimal("220.00"));
            p.setRecords(List.of(tx));
            p.setTotal(1);
            return p;
        });

        PageResult<BalanceTransactionVO> result = service.myTransactions(1L, 1, 10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getTransactionType()).isEqualTo(1);
    }
}
