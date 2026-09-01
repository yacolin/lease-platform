package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.trd.dto.BalanceTransactionVO;
import com.example.leaseplatform.trd.entity.AcctAccount;
import com.example.leaseplatform.trd.entity.TrdBalanceTransaction;
import com.example.leaseplatform.trd.mapper.AcctAccountMapper;
import com.example.leaseplatform.trd.mapper.TrdBalanceTransactionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账户账本单元测试（1.5 账户/钱包）：入账/出账（赠送优先）/冻结/解冻/懒建账户/流水。
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AcctAccountMapper accountMapper;
    @Mock
    private TrdBalanceTransactionMapper txMapper;

    private AccountService service;

    @BeforeAll
    static void initMpEntityCache() {
        initTableInfo(AcctAccount.class);
        initTableInfo(TrdBalanceTransaction.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @BeforeEach
    void setUp() {
        service = new AccountService(accountMapper, txMapper);
    }

    private AcctAccount account(Long available, Long gift, Long frozen) {
        AcctAccount a = new AcctAccount();
        a.setId(1L);
        a.setUserId(1L);
        a.setAvailableBalance(available);
        a.setGiftBalance(gift);
        a.setFrozenBalance(frozen);
        a.setStatus(AcctAccount.STATUS_NORMAL);
        return a;
    }

    private void stubAccount(AcctAccount account) {
        when(accountMapper.selectOne(any(Wrapper.class))).thenReturn(account);
    }

    @Test
    void credit_shouldAddBalanceAndGiftAndWriteTx() {
        stubAccount(account(10000L, 1000L, 0L));

        service.credit(1L, 20000L, 2000L,
                AccountService.TX_RECHARGE, null, 9L, "余额充值");

        ArgumentCaptor<AcctAccount> accountCaptor = ArgumentCaptor.forClass(AcctAccount.class);
        verify(accountMapper).updateById(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getAvailableBalance()).isEqualTo(30000L);
        assertThat(accountCaptor.getValue().getGiftBalance()).isEqualTo(3000L);
        ArgumentCaptor<TrdBalanceTransaction> txCaptor = ArgumentCaptor.forClass(TrdBalanceTransaction.class);
        verify(txMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualTo(22000L);
        assertThat(txCaptor.getValue().getBalanceBefore()).isEqualTo(10000L);
        assertThat(txCaptor.getValue().getFrozenBalanceBefore()).isZero();
        assertThat(txCaptor.getValue().getFrozenBalanceAfter()).isZero();
    }

    @Test
    void credit_lazyCreateAccount_shouldCreateZeroAccountThenCredit() {
        // 账户不存在（新注册用户）→ 懒创建 0 余额账户后再入账
        when(accountMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(accountMapper.insert(any(AcctAccount.class))).thenAnswer(inv -> {
            AcctAccount a = inv.getArgument(0);
            a.setId(1L);
            a.setUserId(1L);
            a.setAvailableBalance(0L);
            a.setGiftBalance(0L);
            a.setFrozenBalance(0L);
            return 1;
        });

        service.credit(1L, 10000L, 1000L, AccountService.TX_RECHARGE, null, null, "充值");

        verify(accountMapper).insert(any(AcctAccount.class));
        verify(accountMapper).updateById(any(AcctAccount.class));
    }

    @Test
    void debit_shouldUseGiftFirstThenBalance() {
        stubAccount(account(10000L, 1000L, 0L));

        service.debit(1L, 3000L, 5L, "咖啡订单");

        ArgumentCaptor<AcctAccount> accountCaptor = ArgumentCaptor.forClass(AcctAccount.class);
        verify(accountMapper).updateById(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getGiftBalance()).isZero();   // 赠送 1000 全扣
        assertThat(accountCaptor.getValue().getAvailableBalance()).isEqualTo(8000L); // 余额扣 2000
        ArgumentCaptor<TrdBalanceTransaction> txCaptor = ArgumentCaptor.forClass(TrdBalanceTransaction.class);
        verify(txMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualTo(-3000L);
        assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(AccountService.TX_CONSUME);
    }

    @Test
    void debit_insufficient_shouldConflictAndNotWrite() {
        stubAccount(account(100L, 0L, 0L));

        assertThatThrownBy(() -> service.debit(1L, 3000L, 5L, "咖啡订单"))
                .isInstanceOf(BizException.class)
                .hasMessage("余额不足");
        verify(accountMapper, never()).updateById(any(AcctAccount.class));
        verify(txMapper, never()).insert(any(TrdBalanceTransaction.class));
    }

    @Test
    void freeze_shouldMoveAvailableToFrozen() {
        stubAccount(account(10000L, 1000L, 0L));

        service.freeze(1L, 4000L, 6L, "会议室预约预授权");

        ArgumentCaptor<AcctAccount> accountCaptor = ArgumentCaptor.forClass(AcctAccount.class);
        verify(accountMapper).updateById(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getAvailableBalance()).isEqualTo(6000L);
        assertThat(accountCaptor.getValue().getFrozenBalance()).isEqualTo(4000L);
        ArgumentCaptor<TrdBalanceTransaction> txCaptor = ArgumentCaptor.forClass(TrdBalanceTransaction.class);
        verify(txMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(AccountService.TX_FREEZE);
        assertThat(txCaptor.getValue().getAmount()).isEqualTo(-4000L);
        assertThat(txCaptor.getValue().getFrozenBalanceBefore()).isZero();
        assertThat(txCaptor.getValue().getFrozenBalanceAfter()).isEqualTo(4000L);
    }

    @Test
    void freeze_insufficient_shouldConflict() {
        stubAccount(account(100L, 0L, 0L));

        assertThatThrownBy(() -> service.freeze(1L, 4000L, 6L, "预授权"))
                .isInstanceOf(BizException.class)
                .hasMessage("余额不足，无法冻结");
        verify(txMapper, never()).insert(any(TrdBalanceTransaction.class));
    }

    @Test
    void unfreeze_shouldMoveFrozenBackToAvailable() {
        stubAccount(account(6000L, 1000L, 4000L));

        service.unfreeze(1L, 4000L, 6L, "会议室预约支付解冻");

        ArgumentCaptor<AcctAccount> accountCaptor = ArgumentCaptor.forClass(AcctAccount.class);
        verify(accountMapper).updateById(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getAvailableBalance()).isEqualTo(10000L);
        assertThat(accountCaptor.getValue().getFrozenBalance()).isZero();
        ArgumentCaptor<TrdBalanceTransaction> txCaptor = ArgumentCaptor.forClass(TrdBalanceTransaction.class);
        verify(txMapper).insert(txCaptor.capture());
        assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(AccountService.TX_UNFREEZE);
        assertThat(txCaptor.getValue().getAmount()).isEqualTo(4000L);
        assertThat(txCaptor.getValue().getFrozenBalanceBefore()).isEqualTo(4000L);
        assertThat(txCaptor.getValue().getFrozenBalanceAfter()).isZero();
    }

    @Test
    void unfreeze_exceedsFrozen_shouldConflict() {
        stubAccount(account(6000L, 1000L, 4000L));

        assertThatThrownBy(() -> service.unfreeze(1L, 5000L, 6L, "解冻"))
                .isInstanceOf(BizException.class)
                .hasMessage("冻结余额不足");
    }

    @Test
    void getOrCreate_existing_shouldReturnAccount() {
        stubAccount(account(100L, 0L, 0L));

        assertThat(service.getOrCreate(1L).getAvailableBalance()).isEqualTo(100L);
        verify(accountMapper, never()).insert(any(AcctAccount.class));
    }

    @Test
    void myTransactions_shouldReturnPaged() {
        when(txMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<TrdBalanceTransaction> p = inv.getArgument(0);
            TrdBalanceTransaction tx = new TrdBalanceTransaction();
            tx.setId(1L);
            tx.setTransactionType(AccountService.TX_RECHARGE);
            tx.setAmount(10000L);
            tx.setFrozenBalanceBefore(0L);
            tx.setFrozenBalanceAfter(0L);
            p.setRecords(List.of(tx));
            p.setTotal(1);
            return p;
        });

        PageResult<BalanceTransactionVO> result = service.myTransactions(1L, 1, 10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getFrozenBalanceBefore()).isZero();
    }
}
