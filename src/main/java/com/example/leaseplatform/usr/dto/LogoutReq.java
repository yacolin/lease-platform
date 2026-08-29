package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登出请求：携带 refresh token 以便服务端作废对应会话（可选）。
 */
@Data
public class LogoutReq {

    @Size(max = 512, message = "refreshToken 过长")
    private String refreshToken;
}
