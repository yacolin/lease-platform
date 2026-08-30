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
import com.example.leaseplatform.trd.entity.TrdRechargeRecord;
import com.example.leaseplatform.trd.entity.TrdRechargeTier;
import com.example.leaseplatform.trd.mapper.TrdRechargeRecordMapper;
import com.example.leaseplatform.trd.mapper.TrdRechargeTierMapper;
import com.example.leaseplatform.trd.service.BalanceService;
import com.example.leaseplatform.usr.entity.UsrMemberLevel;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrMemberLevelMapper;
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
    private UsrMemberLevelMapper memberLevelMapper;
    @Mock
    private TrdRechargeTierMapper rechargeTierMapper;
    @Mock
    private TrdRechargeRecordMapper rechargeRecordMapper;
    @Mock
    private BalanceService balanceService;

    private OrderService service;

    @BeforeAll
    static void initMpEntityCache() {
        // 无 Spring 上下文时 Lambda 条件需手动初始化实体 TableInfo 缓存
        initTableInfo(OrdOrder.class);
        initTableInfo(OrdOrderItem.class);
        initTableInfo(TrdRechargeRecord.class);
        initTableInfo(TrdRechargeTier.class);
        initTableInfo(UsrMemberLevel.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @BeforeEach
    void setUp() {
        // 须在 @Mock 注入后构造（字段初始化器在注入前执行会拿到 null mock）
        service = new OrderService(orderMapper, itemMapper, productMapper, userMapper,
                memberLevelMapper, rechargeTierMapper, rechargeRecordMapper, balanceService);
    }

    private UsrUser user(int memberLevel, Long enterpriseId) {
        UsrUser u = new UsrUser();
        u.setId(1L);
        u.setMemberLevel(memberLevel);
        u.setEnterpriseId(enterpriseId);
        return u;
    }

    private PrdProduct product(Long id, String name, String price) {
        PrdProduct p = new PrdProduct();
        p.setId(id);
        p.setProductName(name);
        p.setPrice(new BigDecimal(price));
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

    private TrdRechargeTier tier(BigDecimal discount) {
        TrdRechargeTier t = new TrdRechargeTier();
        t.setId(2L);
        t.setEquivalentDiscount(discount);
        return t;
    }

    // ==================== 下单 + 折扣叠加 ====================

    @Test
    void create_noMemberNoRecharge_shouldPayFullPrice() {
        when(userMapper.selectById(1L)).thenReturn(user(0, null));
        when(productMapper.selectById(1L)).thenReturn(product(1L, "美式", "12.00"));
        // 非会员 + 无充值 → 原价
        when(rechargeRecordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(orderMapper.insert(any(OrdOrder.class))).thenAnswer(inv -> {
            ((OrdOrder) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(itemMapper.insert(any(OrdOrderItem.class))).thenReturn(1);

        OrderVO vo = service.create(1L, req(1L, 2, Map.of("cup_size", "大杯")));

        assertThat(vo.getTotalAmount()).isEqualByComparingTo("24.00");
        assertThat(vo.getMemberDiscount()).isEqualByComparingTo("0.00");
        assertThat(vo.getRechargeDiscount()).isEqualByComparingTo("0.00");
        assertThat(vo.getPayableAmount()).isEqualByComparingTo("24.00");
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
        when(productMapper.selectById(1L)).thenReturn(product(1L, "美式", "12.00"));
        UsrMemberLevel vip = new UsrMemberLevel();
        vip.setLevelCode("VIP");
        vip.setDiscountRate(new BigDecimal("0.90"));
        when(memberLevelMapper.selectOne(any(Wrapper.class))).thenReturn(vip);
        TrdRechargeRecord latest = new TrdRechargeRecord();
        latest.setTierId(2L);
        when(rechargeRecordMapper.selectOne(any(Wrapper.class))).thenReturn(latest);
        when(rechargeTierMapper.selectById(2L)).thenReturn(tier(new BigDecimal("0.89")));
        when(orderMapper.insert(any(OrdOrder.class))).thenAnswer(inv -> {
            ((OrdOrder) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(itemMapper.insert(any(OrdOrderItem.class))).thenReturn(1);

        OrderVO vo = service.create(1L, req(1L, 2, null));

        assertThat(vo.getMemberDiscount()).isEqualByComparingTo("2.40");   // 24 × 0.10
        assertThat(vo.getRechargeDiscount()).isEqualByComparingTo("2.38"); // 21.6 × 0.11
        assertThat(vo.getPayableAmount()).isEqualByComparingTo("19.22");   // 24 − 2.40 − 2.38
        assertThat(vo.getEnterpriseId()).isEqualTo(5L);
    }

    @Test
    void create_productUnavailable_should400() {
        when(userMapper.selectById(1L)).thenReturn(user(0, null));
        PrdProduct off = product(1L, "美式", "12.00");
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

        OrderVO vo = service.pay(1L, 100L);

        verify(balanceService).debit(1L, new BigDecimal("24.00"), 100L, "咖啡订单");
        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_PICKUP);
        assertThat(vo.getPickupCode()).matches("\\d{6}");
    }

    @Test
    void pay_alreadyPaid_shouldConflict() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, OrderService.STATUS_PICKUP));

        assertThatThrownBy(() -> service.pay(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单已处理");
        verify(balanceService, never()).debit(any(), any(), any(), any());
    }

    @Test
    void cancel_paidOrder_shouldRefund() {
        OrdOrder order = order(100L, OrderService.STATUS_PICKUP);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        OrderVO vo = service.cancel(1L, 100L, "不要了");

        verify(balanceService).credit(1L, new BigDecimal("24.00"), BigDecimal.ZERO,
                BalanceService.TX_REFUND, 100L, null, "订单取消退款");
        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_CANCELLED);
        assertThat(vo.getCancelReason()).isEqualTo("不要了");
    }

    @Test
    void cancel_making_shouldConflict() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, OrderService.STATUS_MAKING));

        assertThatThrownBy(() -> service.cancel(1L, 100L, null))
                .isInstanceOf(BizException.class)
                .hasMessage("订单制作中，暂不可取消");
        verify(balanceService, never()).credit(any(), any(), any(), anyInt(), any(), any(), any());
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

        OrderVO vo = service.adminUpdateStatus(100L, OrderService.STATUS_MAKING);

        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_MAKING);
    }

    @Test
    void adminUpdateStatus_refund_shouldRefund() {
        OrdOrder order = order(100L, OrderService.STATUS_MAKING);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        OrderVO vo = service.adminUpdateStatus(100L, OrderService.STATUS_REFUNDED);

        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_REFUNDED);
        verify(balanceService).credit(1L, new BigDecimal("24.00"), BigDecimal.ZERO,
                BalanceService.TX_REFUND, 100L, null, "商家退款");
    }

    @Test
    void adminUpdateStatus_illegalTransition_shouldConflict() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, OrderService.STATUS_PENDING));

        assertThatThrownBy(() -> service.adminUpdateStatus(100L, OrderService.STATUS_MAKING))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非法的状态流转");
    }

    @Test
    void verifyPickup_shouldComplete() {
        OrdOrder order = order(100L, OrderService.STATUS_PICKUP);
        order.setPickupCode("123456");
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        OrderVO vo = service.verifyPickup("123456");

        assertThat(vo.getOrderStatus()).isEqualTo(OrderService.STATUS_COMPLETED);
    }

    @Test
    void verifyPickup_notFound_should404() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.verifyPickup("999999"))
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
        assertThat(stats.getTodayAmount()).isEqualByComparingTo("24.00");
    }

    private OrdOrder order(Long id, int status) {
        OrdOrder o = new OrdOrder();
        o.setId(id);
        o.setUserId(1L);
        o.setOrderType(1);
        o.setOrderStatus(status);
        o.setPaymentMethod(1); // 余额支付
        o.setTotalAmount(new BigDecimal("24.00"));
        o.setPayableAmount(new BigDecimal("24.00"));
        return o;
    }
}
