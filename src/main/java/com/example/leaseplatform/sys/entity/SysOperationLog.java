package com.example.leaseplatform.sys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（sys_operation_logs）：管理员/企业管理员的后台操作行为。
 * operator_type：1-系统管理员, 2-企业管理员。雪花 ID（海量日志，见 db/README.md 主键 ID 策略）。
 */
@Data
@TableName("sys_operation_logs")
public class SysOperationLog {

    /** 雪花 ID（日志表） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人 ID（后台管理员） */
    private Long operatorId;

    /** 操作人类型：1-系统管理员, 2-企业管理员 */
    private Integer operatorType;

    /** 操作类型（如审核企业、上架商品等） */
    private String operationType;

    /** 操作内容 */
    private String operationContent;

    /** 操作 IP */
    private String ipAddress;

    /** User-Agent */
    private String userAgent;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
