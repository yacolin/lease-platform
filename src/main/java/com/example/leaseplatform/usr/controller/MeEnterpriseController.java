package com.example.leaseplatform.usr.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.usr.dto.EnterpriseInviteVO;
import com.example.leaseplatform.usr.dto.EnterpriseMemberVO;
import com.example.leaseplatform.usr.dto.EnterpriseRegisterReq;
import com.example.leaseplatform.usr.dto.EnterpriseVO;
import com.example.leaseplatform.usr.dto.InviteMemberReq;
import com.example.leaseplatform.usr.dto.MemberPurchaseReq;
import com.example.leaseplatform.usr.dto.MemberPurchaseVO;
import com.example.leaseplatform.usr.dto.SetAdminReq;
import com.example.leaseplatform.usr.service.EnterpriseService;
import com.example.leaseplatform.usr.service.MemberPurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的企业（小程序端 /api/v1/me/enterprise/**，需登录）：
 * 企业注册 / 我的企业 / 员工管理 / 邀请 / 会员购买。
 */
@Tag(name = "myEnterprise", description = "我的企业：当前登录用户的企业：注册/资料/员工/邀请/会员购买（需登录）")
@RestController
@RequestMapping("/api/v1/me/enterprise")
@RequiredArgsConstructor
public class MeEnterpriseController {

    private final EnterpriseService enterpriseService;
    private final MemberPurchaseService purchaseService;

    @Operation(operationId = "registerEnterprise", summary = "企业实名注册（注册人即企业管理员）")
    @PostMapping
    public ApiResponse<EnterpriseVO> register(@Valid @RequestBody EnterpriseRegisterReq req) {
        return ApiResponse.ok(enterpriseService.register(req));
    }

    @Operation(operationId = "getMyEnterprise", summary = "我的企业资料（含审核状态/会员等级）")
    @GetMapping
    public ApiResponse<EnterpriseVO> myEnterprise() {
        return ApiResponse.ok(enterpriseService.myEnterprise());
    }

    // ==================== 员工管理（需企业管理员） ====================

    @Operation(operationId = "listEnterpriseMembers", summary = "企业员工列表")
    @GetMapping("/members")
    public ApiResponse<List<EnterpriseMemberVO>> members() {
        return ApiResponse.ok(enterpriseService.members());
    }

    @Operation(operationId = "inviteEnterpriseMember", summary = "邀请员工（按手机号）")
    @PostMapping("/members")
    public ApiResponse<Void> invite(@Valid @RequestBody InviteMemberReq req) {
        enterpriseService.invite(req);
        return ApiResponse.ok(null);
    }

    @Operation(operationId = "removeEnterpriseMember", summary = "移除员工（需先取消其管理员身份）")
    @DeleteMapping("/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long userId) {
        enterpriseService.removeMember(userId);
        return ApiResponse.ok(null);
    }

    @Operation(operationId = "setEnterpriseAdmin", summary = "设置/取消企业管理员")
    @PutMapping("/members/{userId}/admin")
    public ApiResponse<Void> setAdmin(@PathVariable Long userId,
                                      @Valid @RequestBody SetAdminReq req) {
        enterpriseService.setAdmin(userId, req.getIsAdmin());
        return ApiResponse.ok(null);
    }

    // ==================== 邀请（被邀请人视角） ====================

    @Operation(operationId = "listMyEnterpriseInvites", summary = "我的待处理邀请")
    @GetMapping("/invites")
    public ApiResponse<List<EnterpriseInviteVO>> myInvites() {
        return ApiResponse.ok(enterpriseService.myInvites());
    }

    @Operation(operationId = "acceptEnterpriseInvite", summary = "接受邀请")
    @PostMapping("/invites/{id}/accept")
    public ApiResponse<Void> acceptInvite(@PathVariable Long id) {
        enterpriseService.acceptInvite(id);
        return ApiResponse.ok(null);
    }

    @Operation(operationId = "rejectEnterpriseInvite", summary = "拒绝邀请")
    @PostMapping("/invites/{id}/reject")
    public ApiResponse<Void> rejectInvite(@PathVariable Long id) {
        enterpriseService.rejectInvite(id);
        return ApiResponse.ok(null);
    }

    // ==================== 会员购买（需企业管理员） ====================

    @Operation(operationId = "createMemberPurchase", summary = "会员购买下单（P1 待支付；P2 接微信支付）")
    @PostMapping("/member-purchases")
    public ApiResponse<MemberPurchaseVO> createPurchase(@Valid @RequestBody MemberPurchaseReq req) {
        return ApiResponse.ok(purchaseService.createPurchase(req));
    }

    @Operation(operationId = "mockPayMemberPurchase", summary = "开发 mock 支付（立即生效；P2 由支付回调替代）")
    @PostMapping("/member-purchases/{id}/mock-pay")
    public ApiResponse<MemberPurchaseVO> mockPay(@PathVariable Long id) {
        return ApiResponse.ok(purchaseService.mockPay(id));
    }

    @Operation(operationId = "listMyMemberPurchases", summary = "我的企业购买记录")
    @GetMapping("/member-purchases")
    public ApiResponse<PageResult<MemberPurchaseVO>> myPurchases(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(purchaseService.myPurchases(page, size));
    }
}
