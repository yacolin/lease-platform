package com.example.leaseplatform.usr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.common.cache.CacheSpec;
import com.example.leaseplatform.common.cache.MultiLevelCache;
import com.example.leaseplatform.usr.dto.MemberLevelVO;
import com.example.leaseplatform.usr.dto.MemberPurchaseReq;
import com.example.leaseplatform.usr.dto.MemberPurchaseVO;
import com.example.leaseplatform.usr.entity.UsrEnterprise;
import com.example.leaseplatform.usr.entity.UsrEnterpriseMember;
import com.example.leaseplatform.usr.entity.UsrMemberLevel;
import com.example.leaseplatform.usr.entity.UsrMemberPurchase;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrEnterpriseMapper;
import com.example.leaseplatform.usr.mapper.UsrEnterpriseMemberMapper;
import com.example.leaseplatform.usr.mapper.UsrMemberLevelMapper;
import com.example.leaseplatform.usr.mapper.UsrMemberPurchaseMapper;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import com.example.leaseplatform.security.UserContext;
import com.example.leaseplatform.trd.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 会员购买服务：
 * - 公开：会员等级列表（public，白名单）；
 * - 下单：企业管理员为企业购买（创建待支付单，P2 接入微信支付回调）；
 * - mock 支付（开发模式）：立即置为支付成功并生效（企业 member_level + 到期时间，
 *   同步企业已接受员工 usr_users.member_level）；
 * - 购买记录：我的企业购买历史。
 */
@Service
@RequiredArgsConstructor
public class MemberPurchaseService {

    private static final int PAY_PENDING = 0;
    private static final int PAY_SUCCESS = 1;

    private final UsrMemberLevelMapper levelMapper;
    private final MultiLevelCache cache;
    private final UsrMemberPurchaseMapper purchaseMapper;
    private final UsrEnterpriseMapper enterpriseMapper;
    private final UsrEnterpriseMemberMapper memberMapper;
    private final UsrUserMapper userMapper;
    private final EnterpriseService enterpriseService;
    private final PaymentService paymentService;

    // ==================== 公开：等级列表 ====================

    /**
     * 会员等级列表（仅启用）。
     *
     * <p>整表缓存（L1 + L2），且<b>无需失效</b>：usr_member_levels 在本仓库中没有任何写入点，
     * 完全是种子配置（无管理端接口）。原实现每次请求都走全表扫描 + filesort。
     *
     * <p>该表同时是 §4 所述的「计费规则类参照数据」——预约/下单主链路会反复读取，
     * 若将来接入等级编辑接口，记得在此处补 evict。
     */
    public List<MemberLevelVO> publicLevels() {
        return cache.getList(CacheSpec.MEMBER_LEVEL_LIST, MultiLevelCache.l2Key(CacheSpec.MEMBER_LEVEL_LIST),
                MemberLevelVO.class, CacheSpec.L2_TTL,
                () -> levelMapper.selectList(new LambdaQueryWrapper<UsrMemberLevel>()
                                .eq(UsrMemberLevel::getStatus, 1)
                                .orderByAsc(UsrMemberLevel::getPrice))
                        .stream().map(this::toLevelVO).toList());
    }

    // ==================== 小程序端：下单 / mock 支付 / 记录 ====================

    /** 购买下单：创建待支付记录（P1 提供 mock-pay，P2 接微信支付） */
    @Transactional
    public MemberPurchaseVO createPurchase(MemberPurchaseReq req) {
        UsrEnterprise enterprise = enterpriseService.requireActiveEnterpriseOfAdmin();
        UsrMemberLevel level = levelMapper.selectById(req.getMemberLevelId());
        if (level == null) {
            throw BizException.notFound("会员等级不存在");
        }
        if (level.getStatus() == null || level.getStatus() != 1) {
            throw BizException.badRequest("该会员等级未启用");
        }
        LocalDate today = LocalDate.now();
        UsrMemberPurchase purchase = new UsrMemberPurchase();
        purchase.setPurchaseNo(generateNo("MP"));
        purchase.setOutTradeNo(generateNo("OT"));
        purchase.setEnterpriseId(enterprise.getId());
        purchase.setMemberLevelId(level.getId());
        purchase.setOriginalPrice(level.getPrice());
        purchase.setPayPrice(level.getPrice()); // P1 无优惠
        purchase.setPaymentMethod(1); // 微信支付（P2 接入 JSAPI）
        purchase.setPaymentStatus(PAY_PENDING);
        purchase.setStartDate(today);
        purchase.setEndDate(today.plusYears(1));
        purchaseMapper.insert(purchase);
        // 1.2：同步创建支付单（业务单 → 支付单解耦；微信支付接入前渠道为 mock 直充）
        paymentService.create(UserContext.getUserId(), PaymentService.BIZ_MEMBER_PURCHASE,
                purchase.getId(), purchase.getPayPrice(), PaymentService.METHOD_WECHAT,
                PaymentService.CHANNEL_MOCK, purchase.getOutTradeNo());
        return toVO(purchase, level);
    }

    /**
     * mock 支付（开发模式）：置支付成功并生效会员。
     * P2 接入微信支付后由支付回调 + 主动查单替代本接口。
     */
    @Transactional
    public MemberPurchaseVO mockPay(Long purchaseId) {
        UsrEnterprise enterprise = enterpriseService.requireActiveEnterpriseOfAdmin();
        UsrMemberPurchase purchase = purchaseMapper.selectById(purchaseId);
        if (purchase == null || !purchase.getEnterpriseId().equals(enterprise.getId())) {
            throw BizException.notFound("购买记录不存在");
        }
        if (purchase.getPaymentStatus() == null || purchase.getPaymentStatus() != PAY_PENDING) {
            throw BizException.conflict("该订单已支付");
        }
        UsrMemberLevel level = levelMapper.selectById(purchase.getMemberLevelId());
        if (level == null) {
            throw BizException.notFound("会员等级不存在");
        }
        purchase.setPaymentStatus(PAY_SUCCESS);
        purchase.setPaidAt(LocalDateTime.now());
        purchase.setTransactionId("mock_" + purchase.getPurchaseNo());
        purchaseMapper.updateById(purchase);
        // 1.2：同步结算支付单（幂等）
        paymentService.settleByOutTradeNo(purchase.getOutTradeNo(), "mock_" + purchase.getPurchaseNo());

        applyMembership(enterprise, level, purchase.getEndDate());
        return toVO(purchase, level);
    }

    /** 我的企业购买记录（分页） */
    public PageResult<MemberPurchaseVO> myPurchases(int page, int size) {
        UsrEnterprise enterprise = enterpriseService.requireActiveEnterpriseOfAdmin();
        Page<UsrMemberPurchase> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        purchaseMapper.selectPage(p, new LambdaQueryWrapper<UsrMemberPurchase>()
                .eq(UsrMemberPurchase::getEnterpriseId, enterprise.getId())
                .orderByDesc(UsrMemberPurchase::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream()
                .map(pr -> toVO(pr, levelMapper.selectById(pr.getMemberLevelId())))
                .toList());
    }

    /** 会员生效：更新企业等级/到期时间，同步企业已接受员工的 usr_users.member_level */
    private void applyMembership(UsrEnterprise enterprise, UsrMemberLevel level, LocalDate endDate) {
        int memberLevel = mapLevelCode(level.getLevelCode());
        enterprise.setMemberLevel(memberLevel);
        enterprise.setMemberExpireAt(endDate.atTime(23, 59, 59));
        enterpriseMapper.updateById(enterprise);

        List<UsrEnterpriseMember> members = memberMapper.selectList(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getEnterpriseId, enterprise.getId())
                .eq(UsrEnterpriseMember::getInviteStatus, EnterpriseService.INVITE_ACCEPTED));
        for (UsrEnterpriseMember m : members) {
            UsrUser user = userMapper.selectById(m.getUserId());
            if (user != null) {
                user.setMemberLevel(memberLevel);
                userMapper.updateById(user);
            }
        }
    }

    /** 等级编码 → usr_users/usr_enterprises.member_level 数字（BASIC→1, VIP→2, SVIP→3） */
    public static int mapLevelCode(String levelCode) {
        if (levelCode == null) {
            return 0;
        }
        return switch (levelCode.toUpperCase()) {
            case "BASIC" -> 1;
            case "VIP" -> 2;
            case "SVIP" -> 3;
            default -> 0;
        };
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private MemberLevelVO toLevelVO(UsrMemberLevel l) {
        MemberLevelVO vo = new MemberLevelVO();
        vo.setId(l.getId());
        vo.setLevelCode(l.getLevelCode());
        vo.setLevelName(l.getLevelName());
        vo.setPrice(l.getPrice());
        vo.setDiscountRate(l.getDiscountRate());
        vo.setMonthlyMeetingHours(l.getMonthlyMeetingHours());
        vo.setMeetingBookingAdvanceDays(l.getMeetingBookingAdvanceDays());
        vo.setMeetingPriority(l.getMeetingPriority());
        vo.setMeetingOvertimeFee(l.getMeetingOvertimeFee());
        vo.setDescription(l.getDescription());
        return vo;
    }

    private MemberPurchaseVO toVO(UsrMemberPurchase p, UsrMemberLevel level) {
        MemberPurchaseVO vo = new MemberPurchaseVO();
        vo.setId(p.getId());
        vo.setPurchaseNo(p.getPurchaseNo());
        vo.setOutTradeNo(p.getOutTradeNo());
        vo.setMemberLevelName(level == null ? null : level.getLevelName());
        vo.setMemberLevelCode(level == null ? null : level.getLevelCode());
        vo.setOriginalPrice(p.getOriginalPrice());
        vo.setPayPrice(p.getPayPrice());
        vo.setPaymentMethod(p.getPaymentMethod());
        vo.setPaymentStatus(p.getPaymentStatus());
        vo.setStartDate(p.getStartDate());
        vo.setEndDate(p.getEndDate());
        vo.setPaidAt(TimeUtil.toEpochMillis(p.getPaidAt()));
        vo.setCreatedAt(TimeUtil.toEpochMillis(p.getCreatedAt()));
        return vo;
    }
}
