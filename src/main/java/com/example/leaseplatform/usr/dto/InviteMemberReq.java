package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 邀请员工请求（POST /api/v1/me/enterprise/members）。
 */
@Data
public class InviteMemberReq {

    /** 被邀请人手机号（须已注册小程序用户） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
}
