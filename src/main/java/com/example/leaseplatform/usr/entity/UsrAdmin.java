package com.example.leaseplatform.usr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理员（usr_admins）：管理端登录账号（/api/v1/auth/login，username + password），
 * 与小程序用户（usr_users，/api/v1/auth/wx-login）天然隔离。
 * role：1-超级管理员, 2-运营管理员；status：0-禁用, 1-启用。
 */
@Data
@TableName("usr_admins")
public class UsrAdmin {

    /** 自增 ID（管理员账号量级极小，归账号配置类，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名（唯一） */
    private String username;

    /** 密码 bcrypt 哈希 */
    private String passwordHash;

    /** 姓名 */
    private String name;

    /** 角色：1-超级管理员, 2-运营管理员 */
    private Integer role;

    /** 状态：0-禁用, 1-启用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
