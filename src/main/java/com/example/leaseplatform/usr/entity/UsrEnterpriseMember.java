package com.example.leaseplatform.usr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业员工（usr_enterprise_members）：企业与用户的关联关系。
 * role：0-普通员工, 1-企业管理员；invite_status：0-待接受, 1-已接受, 2-已拒绝。
 */
@Data
@TableName("usr_enterprise_members")
public class UsrEnterpriseMember {

    /** 雪花 ID（关系表，统一类型，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 企业 ID */
    private Long enterpriseId;

    /** 用户 ID */
    private Long userId;

    /** 角色：0-普通员工, 1-企业管理员 */
    private Integer role;

    /** 邀请状态：0-待接受, 1-已接受, 2-已拒绝 */
    private Integer inviteStatus;

    /** 邀请时间 */
    private LocalDateTime invitedAt;

    /** 接受时间 */
    private LocalDateTime acceptedAt;

    /** 逻辑删除：0-未删除, 1-已删除 */
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
