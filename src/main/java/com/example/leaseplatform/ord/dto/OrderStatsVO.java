package com.example.leaseplatform.ord.dto;

import lombok.Data;

/**
 * 订单统计视图对象（商家后台）。
 */
@Data
public class OrderStatsVO {

    /** 今日订单数 */
    private Long todayOrders;

    /** 今日订单金额（分，应付合计） */
    private Long todayAmount;

    /** 待取餐数（status=1） */
    private Long pendingPickupCount;

    /** 制作中数（status=2） */
    private Long makingCount;

    /** 今日已完成数 */
    private Long todayCompleted;

    /** 今日已取消数 */
    private Long todayCancelled;
}
