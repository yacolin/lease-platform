package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新我的资料请求（仅允许更新昵称 / 头像 / 手机号）。
 */
@Data
public class MeUpdateReq {

    @Size(max = 50, message = "昵称最多 50 个字符")
    private String nickname;

    @Size(max = 255, message = "头像 URL 过长")
    private String avatarUrl;

    /** 手机号：留空或 1 开头的 11 位数字 */
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
}
