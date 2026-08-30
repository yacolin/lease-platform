package com.example.leaseplatform.ord.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 取消订单请求（原因可选）。
 */
@Data
public class CancelReq {

    @Size(max = 255, message = "取消原因最多 255 个字符")
    private String reason;
}
