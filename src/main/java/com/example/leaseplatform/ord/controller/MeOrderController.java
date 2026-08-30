package com.example.leaseplatform.ord.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.ord.dto.OrderCreateReq;
import com.example.leaseplatform.ord.dto.OrderVO;
import com.example.leaseplatform.ord.service.OrderService;
import com.example.leaseplatform.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的订单（小程序端 /api/v1/me/orders/**，需登录）：咖啡点单。
 */
@Tag(name = "我的订单", description = "当前登录用户：咖啡下单/余额支付/取消/订单查询（需登录）")
@RestController
@RequestMapping("/api/v1/me/orders")
@RequiredArgsConstructor
public class MeOrderController {

    private final OrderService orderService;

    @Operation(summary = "咖啡下单（商品+规格，待支付）")
    @PostMapping
    public ApiResponse<OrderVO> create(@Valid @RequestBody OrderCreateReq req) {
        return ApiResponse.ok(orderService.create(UserContext.getUserId(), req));
    }

    @Operation(summary = "余额支付（赠送余额优先扣；生成取餐码）")
    @PostMapping("/{id}/pay")
    public ApiResponse<OrderVO> pay(@PathVariable Long id) {
        return ApiResponse.ok(orderService.pay(UserContext.getUserId(), id));
    }

    @Operation(summary = "取消订单（待取餐取消原路退款）")
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderVO> cancel(@PathVariable Long id,
                                       @org.springframework.web.bind.annotation.RequestBody(required = false)
                                       com.example.leaseplatform.ord.dto.CancelReq req) {
        return ApiResponse.ok(orderService.cancel(UserContext.getUserId(), id,
                req == null ? null : req.getReason()));
    }

    @Operation(summary = "我的订单分页")
    @GetMapping
    public ApiResponse<PageResult<OrderVO>> myOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(orderService.myOrders(UserContext.getUserId(), page, size, status));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    public ApiResponse<OrderVO> get(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getMine(UserContext.getUserId(), id));
    }
}
