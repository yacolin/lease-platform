package com.example.leaseplatform.usr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业（usr_enterprises）：企业实名认证信息。
 * audit_status：0-待审核, 1-审核通过, 2-审核拒绝；
 * member_level：0-非会员, 1-基础版, 2-VIP版, 3-SVIP版。
 */
@Data
@TableName("usr_enterprises")
public class UsrEnterprise {

    /** 雪花 ID（企业主体，被用户/订单关联，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 企业名称 */
    private String enterpriseName;

    /** 统一社会信用代码（唯一） */
    private String unifiedSocialCreditCode;

    /** 营业执照图片 URL */
    private String businessLicenseUrl;

    /** 法人姓名 */
    private String legalPersonName;

    /** 法人身份证正面 URL */
    private String legalPersonIdCardFront;

    /** 法人身份证反面 URL */
    private String legalPersonIdCardBack;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人手机号 */
    private String contactPhone;

    /** 企业类型：0-普通, 1-成长型, 2-高价值 */
    private Integer enterpriseType;

    /** 会员等级：0-非会员, 1-基础版, 2-VIP版, 3-SVIP版 */
    private Integer memberLevel;

    /** 会员到期时间 */
    private LocalDateTime memberExpireAt;

    /** 审核状态：0-待审核, 1-审核通过, 2-审核拒绝 */
    private Integer auditStatus;

    /** 审核拒绝原因 */
    private String auditReason;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    /** 状态：0-禁用, 1-正常 */
    private Integer status;

    /** 逻辑删除：0-未删除, 1-已删除 */
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
