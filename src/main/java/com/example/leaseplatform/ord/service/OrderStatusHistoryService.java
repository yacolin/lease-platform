package com.example.leaseplatform.ord.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.ord.dto.OrderStatusHistoryVO;
import com.example.leaseplatform.ord.entity.OrdMealReservation;
import com.example.leaseplatform.ord.entity.OrdOrder;
import com.example.leaseplatform.ord.entity.OrdOrderStatusHistory;
import com.example.leaseplatform.ord.mapper.OrdMealReservationMapper;
import com.example.leaseplatform.ord.mapper.OrdOrderMapper;
import com.example.leaseplatform.ord.mapper.OrdOrderStatusHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单状态历史服务（ord_order_status_history，1.2.3）：
 * 咖啡订单/正餐预订每次状态变更写入历史，所有状态变化可追踪
 * （待支付 → 已支付 → 制作中 → 已完成 / 取消 / 退款）。
 */
@Service
@RequiredArgsConstructor
public class OrderStatusHistoryService {

    /** 业务类型 */
    public static final int BIZ_COFFEE_ORDER = 1;       // 咖啡订单
    public static final int BIZ_MEAL_RESERVATION = 2;   // 正餐预订

    /** 操作人类型 */
    public static final int OPERATOR_USER = 1;          // 用户
    public static final int OPERATOR_ADMIN = 2;         // 商家/系统

    private final OrdOrderStatusHistoryMapper historyMapper;
    private final OrdOrderMapper orderMapper;
    private final OrdMealReservationMapper reservationMapper;

    /** 记录一次状态变更（与业务状态更新同事务） */
    @Transactional
    public void record(int bizType, Long orderId, Integer fromStatus, int toStatus,
                       Long operatorId, int operatorType, String reason) {
        OrdOrderStatusHistory h = new OrdOrderStatusHistory();
        h.setBizType(bizType);
        h.setOrderId(orderId);
        h.setFromStatus(fromStatus);
        h.setToStatus(toStatus);
        h.setOperatorId(operatorId);
        h.setOperatorType(operatorType);
        h.setReason(reason);
        historyMapper.insert(h);
    }

    /** 我的状态历史（校验归属：只能查自己的订单/预订） */
    public List<OrderStatusHistoryVO> mine(Long userId, int bizType, Long orderId) {
        requireOwned(userId, bizType, orderId);
        return listByOrder(bizType, orderId);
    }

    /** 管理端状态历史 */
    public List<OrderStatusHistoryVO> admin(int bizType, Long orderId) {
        return listByOrder(bizType, orderId);
    }

    private void requireOwned(Long userId, int bizType, Long orderId) {
        if (bizType == BIZ_COFFEE_ORDER) {
            OrdOrder order = orderMapper.selectById(orderId);
            if (order == null || !order.getUserId().equals(userId)) {
                throw BizException.notFound("订单不存在");
            }
        } else if (bizType == BIZ_MEAL_RESERVATION) {
            OrdMealReservation reservation = reservationMapper.selectById(orderId);
            if (reservation == null || !reservation.getUserId().equals(userId)) {
                throw BizException.notFound("预订不存在");
            }
        } else {
            throw BizException.badRequest("不支持的业务类型：" + bizType);
        }
    }

    private List<OrderStatusHistoryVO> listByOrder(int bizType, Long orderId) {
        return historyMapper.selectList(new LambdaQueryWrapper<OrdOrderStatusHistory>()
                        .eq(OrdOrderStatusHistory::getBizType, bizType)
                        .eq(OrdOrderStatusHistory::getOrderId, orderId)
                        .orderByAsc(OrdOrderStatusHistory::getId))
                .stream().map(this::toVO).toList();
    }

    private OrderStatusHistoryVO toVO(OrdOrderStatusHistory h) {
        OrderStatusHistoryVO vo = new OrderStatusHistoryVO();
        vo.setId(h.getId());
        vo.setOrderId(h.getOrderId());
        vo.setBizType(h.getBizType());
        vo.setFromStatus(h.getFromStatus());
        vo.setToStatus(h.getToStatus());
        vo.setOperatorId(h.getOperatorId());
        vo.setOperatorType(h.getOperatorType());
        vo.setReason(h.getReason());
        vo.setCreatedAt(TimeUtil.toEpochMillis(h.getCreatedAt()));
        return vo;
    }
}
