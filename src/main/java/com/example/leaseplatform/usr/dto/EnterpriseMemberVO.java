package com.example.leaseplatform.usr.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 企业员工视图对象。
 */
@Data
public class EnterpriseMemberVO {

    private Long userId;

    private String nickname;

    private String phone;

    /** 角色：0-普通员工, 1-企业管理员 */
    @Schema(description = "角色：0-普通员工, 1-企业管理员")
    private Integer role;

    /** 邀请状态：0-待接受, 1-已接受, 2-已拒绝 */
    @Schema(description = "邀请状态：0-待接受, 1-已接受, 2-已拒绝")
    private Integer inviteStatus;

    /** 邀请时间（epoch 毫秒时间戳） */
    private Long invitedAt;

    /** 接受时间（epoch 毫秒时间戳） */
    private Long acceptedAt;
}
