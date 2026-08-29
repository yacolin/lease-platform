package com.example.leaseplatform.usr.dto;

import lombok.Data;

/**
 * 我的待处理企业邀请视图对象（被邀请人视角）。
 */
@Data
public class EnterpriseInviteVO {

    /** 邀请记录 ID（usr_enterprise_members.id） */
    private Long id;

    /** 企业名称 */
    private String enterpriseName;

    /** 邀请时间（epoch 毫秒时间戳） */
    private Long invitedAt;
}
