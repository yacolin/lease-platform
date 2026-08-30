package com.example.leaseplatform.usr.dto;

import lombok.Data;

/**
 * 我的资料（GET/PUT /api/v1/me）：个人资料 + 余额 + 会员等级。
 */
@Data
public class MeVO {

    private Long id;

    private String nickname;

    private String avatarUrl;

    private String phone;

    /** 用户类型：1-超级管理员, 2-企业员工, 3-路人用户 */
    private Integer userType;

    /** 所属企业 ID */
    private Long enterpriseId;

    /** 会员等级：0-非会员, 1-基础版, 2-VIP版, 3-SVIP版 */
    private Integer memberLevel;

    /** 是否企业管理员：0-否, 1-是 */
    private Integer isEnterpriseAdmin;

    /** 余额（分） */
    private Long balance;

    /** 赠送余额（分） */
    private Long giftBalance;

    /** 状态：0-禁用, 1-正常 */
    private Integer status;

    /** 最后登录时间（epoch 毫秒时间戳） */
    private Long lastLoginAt;

    /** 注册时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
