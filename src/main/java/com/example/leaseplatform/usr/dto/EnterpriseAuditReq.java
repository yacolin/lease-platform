package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 企业审核请求（PUT /api/v1/enterprises/{id}/audit，管理端）。
 */
@Data
public class EnterpriseAuditReq {

    /** 审核结果：1-通过, 2-拒绝 */
    @NotNull(message = "审核结果不能为空")
    @Min(value = 1, message = "审核结果：1-通过, 2-拒绝")
    @Max(value = 2, message = "审核结果：1-通过, 2-拒绝")
    private Integer auditStatus;

    /** 拒绝原因（auditStatus=2 时必填，服务层校验） */
    @Size(max = 255, message = "审核原因最多 255 个字符")
    private String auditReason;
}
