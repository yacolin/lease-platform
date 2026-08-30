package com.example.leaseplatform.sys.dto;

import lombok.Data;

/**
 * 操作日志视图对象。
 */
@Data
public class OperationLogVO {

    private Long id;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人类型：1-系统管理员, 2-企业管理员 */
    private Integer operatorType;

    /** 操作类型（如审核企业、上架商品） */
    private String operationType;

    /** 操作内容（参数摘要） */
    private String operationContent;

    /** 操作 IP */
    private String ipAddress;

    /** User-Agent */
    private String userAgent;

    /** 操作时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
