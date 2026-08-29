package com.example.leaseplatform.usr.dto;

import lombok.Data;

/**
 * 企业视图对象（管理端审核 / 我的企业）。
 */
@Data
public class EnterpriseVO {

    private Long id;

    private String enterpriseName;

    private String unifiedSocialCreditCode;

    private String businessLicenseUrl;

    private String legalPersonName;

    private String legalPersonIdCardFront;

    private String legalPersonIdCardBack;

    private String contactName;

    private String contactPhone;

    /** 企业类型：0-普通, 1-成长型, 2-高价值 */
    private Integer enterpriseType;

    /** 会员等级：0-非会员, 1-基础版, 2-VIP版, 3-SVIP版（过期视为 0） */
    private Integer memberLevel;

    /** 会员到期时间（epoch 毫秒时间戳） */
    private Long memberExpireAt;

    /** 审核状态：0-待审核, 1-审核通过, 2-审核拒绝 */
    private Integer auditStatus;

    /** 审核拒绝原因 */
    private String auditReason;

    /** 审核时间（epoch 毫秒时间戳） */
    private Long auditedAt;

    /** 状态：0-禁用, 1-正常 */
    private Integer status;

    /** 注册时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
