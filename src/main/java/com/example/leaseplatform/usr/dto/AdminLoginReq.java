package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台管理员登录请求（/api/v1/auth/login）。
 */
@Data
public class AdminLoginReq {

    /** 登录用户名（usr_admins.username，种子 admin） */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名最多 32 个字符")
    private String username;

    /** 登录密码（bcrypt 校验；种子 admin / 123456） */
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码最多 64 个字符")
    private String password;
}
