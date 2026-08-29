package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员购买下单请求（POST /api/v1/me/enterprise/member-purchases）。
 */
@Data
public class MemberPurchaseReq {

    /** 会员等级 ID（usr_member_levels.id） */
    @NotNull(message = "会员等级不能为空")
    private Long memberLevelId;
}
