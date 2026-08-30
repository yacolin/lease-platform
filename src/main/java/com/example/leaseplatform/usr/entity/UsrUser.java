package com.example.leaseplatform.usr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（usr_users）：所有用户（超级管理员 / 企业员工 / 路人用户）。
 * user_type：1-超级管理员, 2-企业员工, 3-路人用户；
 * member_level：0-非会员, 1-基础版, 2-VIP版, 3-SVIP版；
 * status：0-禁用, 1-正常。
 */
@Data
@TableName("usr_users")
public class UsrUser {

    /** 雪花 ID（用户表是系统根节点，被订单/交易/会议室等所有业务表关联，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 微信 OpenID（唯一） */
    private String openid;

    /** 微信 UnionID */
    private String unionid;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 手机号 */
    private String phone;

    /** 用户类型：1-超级管理员, 2-企业员工, 3-路人用户 */
    private Integer userType;

    /** 所属企业 ID（路人用户为空） */
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

    /** 逻辑删除：0-未删除, 1-已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
