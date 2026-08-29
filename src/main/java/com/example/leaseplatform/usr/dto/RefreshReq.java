package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 刷新令牌请求。
 */
@Data
public class RefreshReq {

    /** refresh token（登录时签发，7 天有效；Redis 中存有对应会话） */
    @NotBlank(message = "refreshToken 不能为空")
    @Size(max = 512, message = "refreshToken 过长")
    private String refreshToken;
}
