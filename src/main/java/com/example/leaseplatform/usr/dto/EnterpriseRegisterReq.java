package com.example.leaseplatform.usr.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 企业实名注册请求（POST /api/v1/me/enterprise）。
 */
@Data
public class EnterpriseRegisterReq {

    @NotBlank(message = "企业名称不能为空")
    @Size(max = 100, message = "企业名称最多 100 个字符")
    private String enterpriseName;

    @NotBlank(message = "统一社会信用代码不能为空")
    @Size(max = 50, message = "统一社会信用代码最多 50 个字符")
    private String unifiedSocialCreditCode;

    @NotBlank(message = "营业执照图片不能为空")
    @Size(max = 255, message = "营业执照图片 URL 过长")
    private String businessLicenseUrl;

    @NotBlank(message = "法人姓名不能为空")
    @Size(max = 50, message = "法人姓名最多 50 个字符")
    private String legalPersonName;

    @NotBlank(message = "法人身份证正面不能为空")
    @Size(max = 255, message = "法人身份证正面 URL 过长")
    private String legalPersonIdCardFront;

    @NotBlank(message = "法人身份证反面不能为空")
    @Size(max = 255, message = "法人身份证反面 URL 过长")
    private String legalPersonIdCardBack;

    @NotBlank(message = "联系人姓名不能为空")
    @Size(max = 50, message = "联系人姓名最多 50 个字符")
    private String contactName;

    @NotBlank(message = "联系人手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "联系人手机号格式不正确")
    private String contactPhone;

    @Min(value = 0, message = "企业类型：0-普通, 1-成长型, 2-高价值")
    @Max(value = 2, message = "企业类型：0-普通, 1-成长型, 2-高价值")
    private Integer enterpriseType = 0;
}
