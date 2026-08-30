package com.example.leaseplatform.ord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 取餐码核销请求。
 */
@Data
public class VerifyPickupReq {

    /** 取餐码（6 位数字） */
    @NotBlank(message = "取餐码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "取餐码为 6 位数字")
    private String pickupCode;
}
