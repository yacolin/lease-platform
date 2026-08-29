package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 微信小程序登录请求（/api/v1/auth/wx-login，code2session）。
 */
@Data
public class WxLoginReq {

    /** 微信登录 code（wx.login 获取；开发 mock 模式下任意非空字符串，固定复用 mock openid） */
    @NotBlank(message = "登录 code 不能为空")
    @Size(max = 64, message = "登录 code 过长")
    private String code;
}
