package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 微信登录请求（code2session）。
 */
@Data
public class LoginReq {

    /** 微信登录 code（wx.login 获取；开发 mock 模式下任意非空字符串） */
    @NotBlank(message = "登录 code 不能为空")
    @Size(max = 64, message = "登录 code 过长")
    private String code;
}
