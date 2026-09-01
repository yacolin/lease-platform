package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.trd.dto.RechargeCreateResultVO;
import com.example.leaseplatform.trd.dto.RechargeRecordVO;
import com.example.leaseplatform.trd.entity.TrdRechargeRecord;
import com.example.leaseplatform.trd.entity.TrdRechargeTier;
import com.example.leaseplatform.trd.mapper.TrdRechargeRecordMapper;
import com.example.leaseplatform.trd.mapper.TrdRechargeTierMapper;
import com.example.leaseplatform.trd.wechat.WechatPayClient;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 充值服务单元测试：下单（配置/未配置）/ mock 直充幂等 / 回调 / 查单 / 入账。
 */
@ExtendWith(MockitoExtension.class)
class RechargeServiceTest {

    @BeforeAll
    static void initMpEntityCache() {
        // 无 Spring 上下文时 Lambda 条件需要手动初始化实体 TableInfo 缓存
        initTableInfo(TrdRechargeRecord.class);
        initTableInfo(TrdRechargeTier.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @Mock
    private TrdRechargeRecordMapper recordMapper;
    @Mock
    private TrdRechargeTierMapper tierMapper;
    @Mock
    private UsrUserMapper userMapper;
    @Mock
    private AccountService accountService;
    @Mock
    private com.example.leaseplatform.trd.service.PaymentService paymentService;
    @Mock
    private com.example.leaseplatform.sys.service.SysIdempotencyService idempotencyService;
    @Mock
    private WechatPayClient wechatPayClient;

    private RechargeService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // 注意：须在 @Mock 注入后构造服务（字段初始化器在注入前执行会拿到 null mock）
        service = new RechargeService(recordMapper, tierMapper, userMapper, accountService,
                paymentService, idempotencyService, wechatPayClient);
    }

    private TrdRechargeTier tier() {
        TrdRechargeTier t = new TrdRechargeTier();
        t.setId(1L);
        t.setRechargeAmount(20000L);
        t.setBonusAmount(2000L);
        t.setActualAmount(22000L);
        t.setStatus(1);
        return t;
    }

    private TrdRechargeRecord record(Long id, int status) {
        TrdRechargeRecord r = new TrdRechargeRecord();
        r.setId(id);
        r.setUserId(1L);
        r.setTierId(1L);
        r.setRechargeAmount(20000L);
        r.setBonusAmount(2000L);
        r.setTotalAmount(22000L);
        r.setOutTradeNo("RC123");
        r.setPaymentStatus(status);
        return r;
    }

    private UsrUser user() {
        UsrUser u = new UsrUser();
        u.setId(1L);
        u.setOpenid("mock_dev_user");
        return u;
    }

    // ==================== 下单 ====================

    @Test
    void createRecharge_unconfigured_shouldReturnRecordWithoutPrepay() {
        when(tierMapper.selectById(1L)).thenReturn(tier());
        when(userMapper.selectById(1L)).thenReturn(user());
        when(wechatPayClient.isConfigured()).thenReturn(false);
        when(recordMapper.insert(any(TrdRechargeRecord.class))).thenAnswer(inv -> {
            ((TrdRechargeRecord) inv.getArgument(0)).setId(100L);
            return 1;
        });

        RechargeCreateResultVO result = service.createRecharge(1L, 1L);

        assertThat(result.getRecord().getPaymentStatus()).isZero();
        assertThat(result.getPrepayParams()).isNull(); // 未配置 → 走 mock-pay
        ArgumentCaptor<TrdRechargeRecord> captor = ArgumentCaptor.forClass(TrdRechargeRecord.class);
        verify(recordMapper).insert(captor.capture());
        assertThat(captor.getValue().getOutTradeNo()).startsWith("RC");
        assertThat(captor.getValue().getTotalAmount()).isEqualTo(22000L);
    }

    @Test
    void createRecharge_configured_shouldReturnPrepayParams() {
        when(tierMapper.selectById(1L)).thenReturn(tier());
        when(userMapper.selectById(1L)).thenReturn(user());
        when(wechatPayClient.isConfigured()).thenReturn(true);
        when(wechatPayClient.createJsapiOrder(eq("mock_dev_user"), any(), eq(20000L), any()))
                .thenReturn("wx-prepay-123");
        when(wechatPayClient.signMessage(any())).thenReturn("pay-sign");
        when(recordMapper.insert(any(TrdRechargeRecord.class))).thenAnswer(inv -> {
            ((TrdRechargeRecord) inv.getArgument(0)).setId(100L);
            return 1;
        });

        RechargeCreateResultVO result = service.createRecharge(1L, 1L);

        assertThat(result.getPrepayParams()).isNotNull();
        assertThat(result.getPrepayParams().getPackageValue()).isEqualTo("prepay_id=wx-prepay-123");
        assertThat(result.getPrepayParams().getPaySign()).isEqualTo("pay-sign");
    }

    @Test
    void createRecharge_tierDisabled_should400() {
        TrdRechargeTier t = tier();
        t.setStatus(0);
        when(tierMapper.selectById(1L)).thenReturn(t);

        assertThatThrownBy(() -> service.createRecharge(1L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("该充值档位已停用");
        verify(recordMapper, never()).insert(any(TrdRechargeRecord.class));
    }

    // ==================== mock 直充 / 入账幂等 ====================

    @Test
    void mockPay_shouldCreditBalanceAndMarkPaid() {
        TrdRechargeRecord r = record(100L, 0);
        when(recordMapper.selectById(100L)).thenReturn(r);
        when(recordMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        RechargeRecordVO vo = service.mockPay(1L, 100L);

        assertThat(vo.getPaymentStatus()).isEqualTo(1);
        verify(accountService).credit(1L, 20000L, 2000L,
                AccountService.TX_RECHARGE, null, 100L, "余额充值");
    }

    @Test
    void mockPay_alreadyPaid_shouldBeIdempotent() {
        TrdRechargeRecord r = record(100L, 1);
        when(recordMapper.selectById(100L)).thenReturn(r);

        RechargeRecordVO vo = service.mockPay(1L, 100L);

        assertThat(vo.getPaymentStatus()).isEqualTo(1);
        verify(accountService, never()).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void mockPay_concurrent_shouldNotDoubleCredit() {
        // 乐观更新影响 0 行 → 视为并发已处理，不重复入账（selectById 依次返回待支付 → 已支付）
        when(recordMapper.selectById(100L)).thenReturn(record(100L, 0), record(100L, 1));
        when(recordMapper.update(any(), any(Wrapper.class))).thenReturn(0);

        RechargeRecordVO vo = service.mockPay(1L, 100L);

        assertThat(vo.getPaymentStatus()).isEqualTo(1);
        verify(accountService, never()).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void mockPay_notOwnRecord_should404() {
        TrdRechargeRecord other = record(100L, 0);
        other.setUserId(99L);
        when(recordMapper.selectById(100L)).thenReturn(other);

        assertThatThrownBy(() -> service.mockPay(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessage("充值记录不存在");
    }

    // ==================== 回调 / 查单 ====================

    @Test
    void handleNotify_success_shouldSettle() {
        when(wechatPayClient.isConfigured()).thenReturn(true);
        when(wechatPayClient.decryptNotify(any()))
                .thenReturn("{\"out_trade_no\":\"RC123\",\"transaction_id\":\"WX001\",\"trade_state\":\"SUCCESS\"}");
        TrdRechargeRecord r = record(100L, 0);
        when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(r);
        when(recordMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(idempotencyService.acquire(any(), any(), any(), any(), any())).thenReturn(true);

        String body = """
                {"event_type":"TRANSACTION.SUCCESS",
                 "resource":{"ciphertext":"x","nonce":"n","associated_data":"a"}}""";
        service.handleNotify(body);

        verify(accountService).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
        // 1.2：回调幂等键落库 + 支付单结算
        verify(idempotencyService).acquire(eq("WX_NOTIFY:RC123"), eq("RECHARGE_NOTIFY"),
                eq(100L), any(), any());
        verify(paymentService).settleByOutTradeNo("RC123", "WX001");
        verify(idempotencyService).complete("WX_NOTIFY:RC123", "OK");
    }

    @Test
    void handleNotify_duplicate_shouldSkip() {
        // 重复回调：幂等键已存在 → 直接跳过，不重复入账
        when(wechatPayClient.isConfigured()).thenReturn(true);
        when(wechatPayClient.decryptNotify(any()))
                .thenReturn("{\"out_trade_no\":\"RC123\",\"transaction_id\":\"WX001\",\"trade_state\":\"SUCCESS\"}");
        when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(record(100L, 0));
        when(idempotencyService.acquire(any(), any(), any(), any(), any())).thenReturn(false);

        service.handleNotify("""
                {"event_type":"TRANSACTION.SUCCESS",
                 "resource":{"ciphertext":"x","nonce":"n","associated_data":"a"}}""");

        verify(accountService, never()).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
        verify(paymentService, never()).settleByOutTradeNo(any(), any());
    }

    @Test
    void handleNotify_notConfigured_should400() {
        when(wechatPayClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.handleNotify("{}"))
                .isInstanceOf(BizException.class)
                .hasMessage("微信支付未配置");
    }

    @Test
    void handleNotify_nonSuccessEvent_shouldIgnore() {
        when(wechatPayClient.isConfigured()).thenReturn(true);

        service.handleNotify("{\"event_type\":\"REFUND.SUCCESS\"}");

        verify(recordMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void query_unconfigured_shouldReturnLocalState() {
        TrdRechargeRecord r = record(100L, 0);
        when(recordMapper.selectById(100L)).thenReturn(r);
        when(wechatPayClient.isConfigured()).thenReturn(false);

        RechargeRecordVO vo = service.query(1L, 100L);

        assertThat(vo.getPaymentStatus()).isZero();
    }

    @Test
    void query_configuredSuccess_shouldSettle() {
        TrdRechargeRecord r = record(100L, 0);
        when(recordMapper.selectById(100L)).thenReturn(r);
        when(wechatPayClient.isConfigured()).thenReturn(true);
        Map<?, ?> wxResp = Map.of("trade_state", "SUCCESS", "transaction_id", "WX002");
        org.mockito.Mockito.doReturn(wxResp).when(wechatPayClient).queryOrder("RC123");
        when(recordMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        RechargeRecordVO vo = service.query(1L, 100L);

        assertThat(vo.getPaymentStatus()).isEqualTo(1);
        verify(accountService).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
    }
}
