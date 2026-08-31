package com.example.leaseplatform.ord.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.ord.dto.OrderCreateReq;
import com.example.leaseplatform.ord.dto.OrderItemReq;
import com.example.leaseplatform.ord.dto.OrderVO;
import com.example.leaseplatform.ord.entity.OrdOrder;
import com.example.leaseplatform.ord.entity.OrdOrderItem;
import com.example.leaseplatform.ord.mapper.OrdOrderItemMapper;
import com.example.leaseplatform.ord.mapper.OrdOrderMapper;
import com.example.leaseplatform.prd.entity.PrdProduct;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import com.example.leaseplatform.trd.entity.TrdPayment;
import com.example.leaseplatform.trd.entity.TrdRefund;
import com.example.leaseplatform.trd.service.BalanceService;
import com.example.leaseplatform.trd.service.PaymentService;
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

import java.math.BigDecimal;
import java.util.List;
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
 * 咖啡点单服务单元测试：下单/折扣叠加/支付/取消退款/状态流转/核销。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrdOrderMapper orderMapper;
    @Mock
    private OrdOrderItemMapper itemMapper;
    @Mock
    private PrdProductMapper productMapper;
    @Mock
    private UsrUserMapper userMapper;
    @Mock
    private DiscountCalculator discountCalculator;
    @Mock
    private BalanceService balanceService;
    @Mock
    private com.example.leaseplatform.trd.service.PaymentService paymentService;
    @Mock
    private com.example.leaseplatform.trd.service.RefundService refundService;
    @Mock
    private OrderStatusHistoryService statusHistoryService;

    private OrderService service;

    @BeforeAll
    static void initMpEntityCache() {
        // 无 Spring 上下文时 Lambda 条件需手动初始化实体 TableInfo 缓存
        initTableInfo(OrdOrder.class);
        initTableInfo(OrdOrderItem.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @BeforeEach
    void setUp() {
        // 须在 @Mock 注入后构造（字段初始化器在注入前执行会拿到 null mock）
        service = new OrderService(orderMapper, itemMapper, productMapper, userMapper,
                discountCalculator, balanceService, paymentService, refundService, statusHistoryService);
    }

    private UsrUser user(int memberLevel, Long enterpriseId) {
        UsrUser u = new UsrUser();
        u.setId(1L);
        u.setMemberLevel(memberLevel);
        u.setEnterpriseId(enterpriseId);
        return u;
    }

    private PrdProduct product(Long id, String name, long priceCents) {
        PrdProduct p = new PrdProduct();
        p.setId(id);
        p.setProductName(name);
        p.setPrice(priceCents);
        p.setIsAvailable(1);
        return p;
    }

    private OrderCreateReq req(Long productId, int qty, Map<String, Object> spec) {
        OrderCreateReq req = new OrderCreateReq();
        OrderItemReq item = new OrderItemReq();
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setSpec(spec);
        req.setItems(List.of(item));
        return req;
    }

    // ==================== 下单 + 折扣叠加 ====================

    @Test
    void create_noMemberNoRecharge_shouldPayFullPrice() {
        when(userMapper.selectById(1L)).thenReturn(user(0, null));
        when(productMapper.selectById(1L)).thenReturn(product(1L, "美式", 1200L));
        // 非会员 + 无充值 → 原价
        when(discountCalculator.memberDiscountRate(any())).thenReturn(BigDecimal.ONE);
        when(discountCalculator.rechargeDiscountRate(1L)).thenReturn(BigDecimal.ONE);
        when(orderMapper.insert(any(OrdOrder.class))).thenAnswer(inv -> {
            ((OrdOrder) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(itemMapper.insert(any(OrdOrderItem.class))).thenReturn(1);

        OrderVO vo = service.create(1L, req(1L, 2, Map.of("cup_size", "大杯")));

        assertThat(vo.getTotalAmount()).isEqualTo(2400L);
        assertThat(vo.getMemberDiscount()).isEqualTo(0L);
        assertThat(vo.getRechargeDiscount()).isEqualTo(0L);
        assertThat(vo.getPayableAmount()).isEqualTo(2400L);
        assertThat(vo.getOrderStatus()).isZero();
        // 明细规格快照
        ArgumentCaptor<OrdOrderItem> captor = ArgumentCaptor.forClass(OrdOrderItem.class);
        verify(itemMapper).insert(captor.capture());
        assertThat(captor.getValue().getSpecification()).contains("大杯");
    }

    @Test
    void create_memberAndRecharge_shouldStackDiscounts() {
        // VIP 会员 0.90 × 充值 0.89 → 应付 = 24 × 0.801 = 19.22
        when(userMapper.selectById(1L)).thenReturn(user(2, 5L));
        when(productMapper.selectById(1L)).thenReturn(product(1L, "美式", 1200L));
        when(discountCalculator.memberDiscountRate(any())).thenReturn(new BigDecimal("0.90"));
        when(discountCalculator.rechargeDiscountRate(1L)).thenReturn(new BigDecimal("0.89"));
        when(orderMapper.insert(any(OrdOrder.class))).thenAnswer(inv -> {
            ((OrdOrder) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(itemMapper.insert(any(OrdOrderItem.class))).thenReturn(1);

        OrderVO vo = service.create(1L, req(1L, 2, null));

        assertThat(vo.getMemberDiscount()).isEqualTo(240L);   // 24 × 0.10
        assertThat(vo.getRechargeDiscount()).isEqualTo(238L); // 21.6 × 0.11
        assertThat(vo.getPayableAmount()).isEqualTo(1922L);   // 24 − 2.40 − 2.38
        assertThat(vo.getEnterpriseId()).isEqualTo(5L);
    }

    @Test
    void create_productUnavailable_should400() {
        when(userMapper.selectById(1L)).thenReturn(user(0, null));
        PrdProduct off = product(1L, "美式", 1200L);
        off.setIsAvailable(0);
        when(productMapper.selectById(1L)).thenReturn(off);

        assertThatThrownBy(() -> service.create(1L, req(1L, 1, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("商品已下架");
        verify(orderMapper, never()).insert(any(OrdOrder.class));
    }

    // ==================== 支付 / 取消 ====================

    @Test
    void pay_shouldDebitAndGeneratePickupCode() {
        OrdOrder order = order(100L, OrderService.STATUS_PENDING);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderMapper.selectCount(any(Wrapper.class))).thenReturn(0L); // 取餐码查重
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1); // 乐观锁定 0→1
        TrdPayment payment = new TrdPayment();
        payment.setId(900L);
        when(paymentService.create(any(), anyInt(), any(), anyLong(), anyInt(), anyInt(), any()))
                .thenReturn(payment);

        OrderVO vo = service.pay(1L, 100L);

        verify(balanceService).debit(1L, 2400L, 100L, "咖啡订单");
        // 1.2：创建并结算支付单
        verify(paymentService).create(eq(1L), eq(PaymentService.BIZ_ORDER), eq(100L),
                eq(2400L), eq(PaymentService.METHOD_BALANCE), eq(PaymentService.CHANNEL_BALANCE), any());
        verify(paymentService).settle(900L, null);
        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_PICKUP);
        assertThat(vo.getPickupCode()).matches("\\d{6}");
    }

    @Test
    void pay_concurrentDuplicate_shouldConflictAndNotSettle() {
        // 并发：先扣款、乐观更新失败 → 抛"订单已处理"，本事务回滚扣款（生产由事务保证）
        OrdOrder order = order(100L, OrderService.STATUS_PENDING);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(orderMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(0); // 并发：乐观更新失败

        assertThatThrownBy(() -> service.pay(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单已处理");
        verify(balanceService).debit(1L, 2400L, 100L, "咖啡订单");
        verify(paymentService, never()).settle(any(), any());
        verify(paymentService, never()).create(any(), anyInt(), any(), anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void pay_alreadyPaid_shouldConflict() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, OrderService.STATUS_PICKUP));

        assertThatThrownBy(() -> service.pay(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单已处理");
        verify(balanceService, never()).debit(any(), anyLong(), any(), any());
    }

    @Test
    void cancel_paidOrderNoPayment_shouldLegacyRefund() {
        // 1.2 之前的历史订单无支付单：回退为直接余额入账
        OrdOrder order = order(100L, OrderService.STATUS_PICKUP);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(paymentService.getByBiz(anyInt(), any())).thenReturn(null);

        OrderVO vo = service.cancel(1L, 100L, "不要了");

        verify(balanceService).credit(1L, 2400L, 0L,
                BalanceService.TX_REFUND, 100L, null, "订单取消退款");
        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_CANCELLED);
        assertThat(vo.getCancelReason()).isEqualTo("不要了");
    }

    @Test
    void cancel_paidOrder_shouldRefundViaRefundService() {
        // 1.2：有支付单 → 走退款单（幂等键防重复）
        OrdOrder order = order(100L, OrderService.STATUS_PICKUP);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        TrdPayment payment = new TrdPayment();
        payment.setId(900L);
        when(paymentService.getByBiz(PaymentService.BIZ_ORDER, 100L)).thenReturn(payment);
        when(refundService.refundToBalance(any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(new TrdRefund());

        OrderVO vo = service.cancel(1L, 100L, "不要了");

        verify(refundService).refundToBalance(1L, 900L, 2400L, "订单取消退款",
                "ORDER_CANCEL_REFUND:100", 100L);
        verify(balanceService, never()).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_CANCELLED);
    }

    @Test
    void cancel_making_shouldConflict() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, OrderService.STATUS_MAKING));

        assertThatThrownBy(() -> service.cancel(1L, 100L, null))
                .isInstanceOf(BizException.class)
                .hasMessage("订单制作中，暂不可取消");
        verify(balanceService, never()).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void cancel_notOwnOrder_should404() {
        OrdOrder other = order(100L, OrderService.STATUS_PICKUP);
        other.setUserId(99L);
        when(orderMapper.selectById(100L)).thenReturn(other);

        assertThatThrownBy(() -> service.cancel(1L, 100L, null))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    // ==================== 商家状态 / 核销 ====================

    @Test
    void adminUpdateStatus_making_shouldAdvance() {
        OrdOrder order = order(100L, OrderService.STATUS_PICKUP);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        OrderVO vo = service.adminUpdateStatus(100L, OrderService.STATUS_MAKING, 9L);

        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_MAKING);
        // 1.2：状态历史留痕
        verify(statusHistoryService).record(eq(OrderStatusHistoryService.BIZ_COFFEE_ORDER),
                eq(100L), eq(OrderService.STATUS_PICKUP), eq(OrderService.STATUS_MAKING),
                eq(9L), eq(OrderStatusHistoryService.OPERATOR_ADMIN), any());
    }

    @Test
    void adminUpdateStatus_refundNoPayment_shouldLegacyRefund() {
        OrdOrder order = order(100L, OrderService.STATUS_MAKING);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(paymentService.getByBiz(anyInt(), any())).thenReturn(null);

        OrderVO vo = service.adminUpdateStatus(100L, OrderService.STATUS_REFUNDED, 9L);

        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_REFUNDED);
        verify(balanceService).credit(1L, 2400L, 0L,
                BalanceService.TX_REFUND, 100L, null, "商家退款");
    }

    @Test
    void adminUpdateStatus_refund_shouldRefundViaRefundService() {
        OrdOrder order = order(100L, OrderService.STATUS_MAKING);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        TrdPayment payment = new TrdPayment();
        payment.setId(900L);
        when(paymentService.getByBiz(PaymentService.BIZ_ORDER, 100L)).thenReturn(payment);
        when(refundService.refundToBalance(any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(new TrdRefund());

        OrderVO vo = service.adminUpdateStatus(100L, OrderService.STATUS_REFUNDED, 9L);

        verify(refundService).refundToBalance(1L, 900L, 2400L, "商家退款",
                "ORDER_ADMIN_REFUND:100", 100L);
        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_REFUNDED);
    }

    @Test
    void adminUpdateStatus_illegalTransition_shouldConflict() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, OrderService.STATUS_PENDING));

        assertThatThrownBy(() -> service.adminUpdateStatus(100L, OrderService.STATUS_MAKING, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非法的状态流转");
    }

    @Test
    void verifyPickup_shouldComplete() {
        OrdOrder order = order(100L, OrderService.STATUS_PICKUP);
        order.setPickupCode("123456");
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        OrderVO vo = service.verifyPickup("123456", 9L);

        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_COMPLETED);
        verify(statusHistoryService).record(eq(OrderStatusHistoryService.BIZ_COFFEE_ORDER),
                eq(100L), eq(OrderService.STATUS_PICKUP), eq(OrderService.STATUS_COMPLETED),
                eq(9L), eq(OrderStatusHistoryService.OPERATOR_ADMIN), eq("取餐码核销"));
    }

    @Test
    void verifyPickup_notFound_should404() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.verifyPickup("999999", 9L))
                .isInstanceOf(BizException.class)
                .hasMessage("取餐码不存在");
    }

    // ==================== 查询 / 统计 ====================

    @Test
    void myOrders_shouldReturnPagedWithItems() {
        when(orderMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<OrdOrder> p = inv.getArgument(0);
            p.setRecords(List.of(order(100L, OrderService.STATUS_PICKUP)));
            p.setTotal(1);
            return p;
        });
        OrdOrderItem item = new OrdOrderItem();
        item.setOrderId(100L);
        item.setProductName("美式");
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));

        PageResult<OrderVO> result = service.myOrders(1L, 1, 10, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getItems()).hasSize(1);
    }

    @Test
    void stats_shouldAggregate() {
        when(orderMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        OrdOrder order = order(100L, OrderService.STATUS_COMPLETED);
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(order));

        var stats = service.stats();

        assertThat(stats.getTodayOrders()).isEqualTo(1L);
        assertThat(stats.getTodayAmount()).isEqualTo(2400L);
    }

    private OrdOrder order(Long id, int status) {
        OrdOrder o = new OrdOrder();
        o.setId(id);
        o.setUserId(1L);
        o.setOrderType(1);
        o.setOrderStatus(status);
        o.setPaymentMethod(1); // 余额支付
        o.setTotalAmount(2400L);
        o.setPayableAmount(2400L);
        return o;
    }
}
