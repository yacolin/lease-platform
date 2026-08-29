package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设置/取消企业管理员请求。
 */
@Data
public class SetAdminReq {

    /** true-设为管理员, false-取消管理员 */
    @NotNull(message = "isAdmin 不能为空")
    private Boolean isAdmin;
}
