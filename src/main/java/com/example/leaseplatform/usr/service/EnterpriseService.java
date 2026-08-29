package com.example.leaseplatform.usr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.security.UserContext;
import com.example.leaseplatform.usr.dto.EnterpriseAuditReq;
import com.example.leaseplatform.usr.dto.EnterpriseInviteVO;
import com.example.leaseplatform.usr.dto.EnterpriseMemberVO;
import com.example.leaseplatform.usr.dto.EnterpriseRegisterReq;
import com.example.leaseplatform.usr.dto.EnterpriseVO;
import com.example.leaseplatform.usr.dto.InviteMemberReq;
import com.example.leaseplatform.usr.entity.UsrEnterprise;
import com.example.leaseplatform.usr.entity.UsrEnterpriseMember;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrEnterpriseMapper;
import com.example.leaseplatform.usr.mapper.UsrEnterpriseMemberMapper;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 企业域服务：企业实名注册、我的企业、员工管理（邀请/接受/拒绝/移除/管理员）、
 * 管理端审核。员工关系以 usr_enterprise_members（role + invite_status）为准，
 * 注册人与接受邀请者同步维护 usr_users.enterprise_id / is_enterprise_admin。
 */
@Service
@RequiredArgsConstructor
public class EnterpriseService {

    public static final int INVITE_PENDING = 0;
    public static final int INVITE_ACCEPTED = 1;
    public static final int INVITE_REJECTED = 2;

    public static final int ROLE_EMPLOYEE = 0;
    public static final int ROLE_ADMIN = 1;

    private final UsrEnterpriseMapper enterpriseMapper;
    private final UsrEnterpriseMemberMapper memberMapper;
    private final UsrUserMapper userMapper;

    // ==================== 小程序端：企业注册 / 我的企业 ====================

    /** 企业实名注册：创建待审核企业，注册人成为企业管理员 */
    @Transactional
    public EnterpriseVO register(EnterpriseRegisterReq req) {
        Long userId = UserContext.getUserId();
        // 已加入企业（已接受成员关系）不可重复注册
        Long joined = memberMapper.selectCount(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getUserId, userId)
                .eq(UsrEnterpriseMember::getInviteStatus, INVITE_ACCEPTED));
        if (joined != null && joined > 0) {
            throw BizException.conflict("您已加入企业，不能重复注册");
        }
        // 统一社会信用代码唯一（未删除记录）
        Long dup = enterpriseMapper.selectCount(new LambdaQueryWrapper<UsrEnterprise>()
                .eq(UsrEnterprise::getUnifiedSocialCreditCode, req.getUnifiedSocialCreditCode()));
        if (dup != null && dup > 0) {
            throw BizException.conflict("该统一社会信用代码已注册");
        }

        UsrEnterprise enterprise = new UsrEnterprise();
        enterprise.setEnterpriseName(req.getEnterpriseName());
        enterprise.setUnifiedSocialCreditCode(req.getUnifiedSocialCreditCode());
        enterprise.setBusinessLicenseUrl(req.getBusinessLicenseUrl());
        enterprise.setLegalPersonName(req.getLegalPersonName());
        enterprise.setLegalPersonIdCardFront(req.getLegalPersonIdCardFront());
        enterprise.setLegalPersonIdCardBack(req.getLegalPersonIdCardBack());
        enterprise.setContactName(req.getContactName());
        enterprise.setContactPhone(req.getContactPhone());
        enterprise.setEnterpriseType(req.getEnterpriseType() == null ? 0 : req.getEnterpriseType());
        enterprise.setMemberLevel(0);
        enterprise.setAuditStatus(0); // 待审核
        enterprise.setStatus(1);
        enterpriseMapper.insert(enterprise);

        // 注册人 = 企业管理员（已接受）
        insertMember(enterprise.getId(), userId, ROLE_ADMIN, INVITE_ACCEPTED);

        // 同步用户企业关联
        UsrUser user = requireUser(userId);
        user.setEnterpriseId(enterprise.getId());
        user.setIsEnterpriseAdmin(1);
        userMapper.updateById(user);
        return toVO(enterprise);
    }

    /** 我的企业（当前用户已接受成员关系所在企业；不要求审核通过，便于展示待审核状态） */
    public EnterpriseVO myEnterprise() {
        UsrEnterpriseMember member = acceptedMemberOf(UserContext.getUserId());
        UsrEnterprise enterprise = enterpriseMapper.selectById(member.getEnterpriseId());
        if (enterprise == null) {
            throw BizException.notFound("企业不存在");
        }
        return toVO(enterprise);
    }

    // ==================== 小程序端：员工管理 ====================

    /** 企业员工列表（需企业管理员，企业已通过审核） */
    public List<EnterpriseMemberVO> members() {
        UsrEnterprise enterprise = requireActiveEnterpriseOfAdmin();
        List<UsrEnterpriseMember> members = memberMapper.selectList(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getEnterpriseId, enterprise.getId())
                .orderByDesc(UsrEnterpriseMember::getRole)
                .orderByAsc(UsrEnterpriseMember::getAcceptedAt));
        Map<Long, UsrUser> users = usersOf(members);
        return members.stream().map(m -> {
            UsrUser u = users.get(m.getUserId());
            EnterpriseMemberVO vo = new EnterpriseMemberVO();
            vo.setUserId(m.getUserId());
            vo.setNickname(u == null ? null : u.getNickname());
            vo.setPhone(u == null ? null : u.getPhone());
            vo.setRole(m.getRole());
            vo.setInviteStatus(m.getInviteStatus());
            vo.setInvitedAt(TimeUtil.toEpochMillis(m.getInvitedAt()));
            vo.setAcceptedAt(TimeUtil.toEpochMillis(m.getAcceptedAt()));
            return vo;
        }).toList();
    }

    /** 邀请员工：按手机号（被邀请人须已注册小程序用户） */
    @Transactional
    public void invite(InviteMemberReq req) {
        UsrEnterprise enterprise = requireActiveEnterpriseOfAdmin();
        UsrUser target = userMapper.selectOne(new LambdaQueryWrapper<UsrUser>()
                .eq(UsrUser::getPhone, req.getPhone()));
        if (target == null) {
            throw BizException.badRequest("该手机号未注册小程序用户");
        }
        // 目标用户已加入其他企业
        Long other = memberMapper.selectCount(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getUserId, target.getId())
                .eq(UsrEnterpriseMember::getInviteStatus, INVITE_ACCEPTED));
        if (other != null && other > 0) {
            throw BizException.conflict("该用户已加入其他企业");
        }
        // 本企业已有关系：待接受/已接受 → 409；已拒绝 → 重新邀请
        UsrEnterpriseMember existing = memberMapper.selectOne(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getEnterpriseId, enterprise.getId())
                .eq(UsrEnterpriseMember::getUserId, target.getId()));
        if (existing != null) {
            if (existing.getInviteStatus() != INVITE_REJECTED) {
                throw BizException.conflict("该用户已在本企业（或待接受邀请）");
            }
            existing.setInviteStatus(INVITE_PENDING);
            existing.setInvitedAt(LocalDateTime.now());
            existing.setAcceptedAt(null);
            memberMapper.updateById(existing);
            return;
        }
        insertMember(enterprise.getId(), target.getId(), ROLE_EMPLOYEE, INVITE_PENDING);
    }

    /** 我的待处理邀请（被邀请人视角） */
    public List<EnterpriseInviteVO> myInvites() {
        Long userId = UserContext.getUserId();
        List<UsrEnterpriseMember> pending = memberMapper.selectList(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getUserId, userId)
                .eq(UsrEnterpriseMember::getInviteStatus, INVITE_PENDING)
                .orderByDesc(UsrEnterpriseMember::getInvitedAt));
        return pending.stream().map(m -> {
            UsrEnterprise e = enterpriseMapper.selectById(m.getEnterpriseId());
            EnterpriseInviteVO vo = new EnterpriseInviteVO();
            vo.setId(m.getId());
            vo.setEnterpriseName(e == null ? null : e.getEnterpriseName());
            vo.setInvitedAt(TimeUtil.toEpochMillis(m.getInvitedAt()));
            return vo;
        }).toList();
    }

    /** 接受邀请：企业须已通过审核 */
    @Transactional
    public void acceptInvite(Long memberId) {
        Long userId = UserContext.getUserId();
        UsrEnterpriseMember member = requireOwnInvite(memberId, userId);
        if (member.getInviteStatus() != INVITE_PENDING) {
            throw BizException.conflict("该邀请已处理");
        }
        UsrEnterprise enterprise = enterpriseMapper.selectById(member.getEnterpriseId());
        if (enterprise == null || enterprise.getAuditStatus() == null || enterprise.getAuditStatus() != 1) {
            throw BizException.conflict("企业尚未通过审核，无法接受邀请");
        }
        Long joined = memberMapper.selectCount(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getUserId, userId)
                .eq(UsrEnterpriseMember::getInviteStatus, INVITE_ACCEPTED));
        if (joined != null && joined > 0) {
            throw BizException.conflict("您已加入其他企业");
        }
        member.setInviteStatus(INVITE_ACCEPTED);
        member.setAcceptedAt(LocalDateTime.now());
        memberMapper.updateById(member);

        UsrUser user = requireUser(userId);
        user.setEnterpriseId(enterprise.getId());
        user.setIsEnterpriseAdmin(0); // 普通员工
        syncMemberLevel(user, enterprise);
        userMapper.updateById(user);
    }

    /** 拒绝邀请 */
    @Transactional
    public void rejectInvite(Long memberId) {
        Long userId = UserContext.getUserId();
        UsrEnterpriseMember member = requireOwnInvite(memberId, userId);
        if (member.getInviteStatus() != INVITE_PENDING) {
            throw BizException.conflict("该邀请已处理");
        }
        member.setInviteStatus(INVITE_REJECTED);
        memberMapper.updateById(member);
    }

    /** 移除员工（不能移除自己；需先取消其管理员身份） */
    @Transactional
    public void removeMember(Long targetUserId) {
        Long operatorId = UserContext.getUserId();
        UsrEnterprise enterprise = requireActiveEnterpriseOfAdmin();
        if (targetUserId.equals(operatorId)) {
            throw BizException.badRequest("不能移除自己");
        }
        UsrEnterpriseMember member = memberMapper.selectOne(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getEnterpriseId, enterprise.getId())
                .eq(UsrEnterpriseMember::getUserId, targetUserId));
        if (member == null) {
            throw BizException.notFound("该员工不在本企业");
        }
        if (member.getRole() != null && member.getRole() == ROLE_ADMIN) {
            throw BizException.conflict("请先取消该员工的企业管理员身份");
        }
        memberMapper.deleteById(member.getId());
        clearUserEnterprise(targetUserId);
    }

    /** 设置/取消企业管理员 */
    @Transactional
    public void setAdmin(Long targetUserId, Boolean isAdmin) {
        UsrEnterprise enterprise = requireActiveEnterpriseOfAdmin();
        UsrEnterpriseMember member = memberMapper.selectOne(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getEnterpriseId, enterprise.getId())
                .eq(UsrEnterpriseMember::getUserId, targetUserId));
        if (member == null) {
            throw BizException.notFound("该员工不在本企业");
        }
        if (member.getInviteStatus() != INVITE_ACCEPTED) {
            throw BizException.conflict("该员工尚未接受邀请");
        }
        member.setRole(Boolean.TRUE.equals(isAdmin) ? ROLE_ADMIN : ROLE_EMPLOYEE);
        memberMapper.updateById(member);

        UsrUser user = requireUser(targetUserId);
        user.setIsEnterpriseAdmin(Boolean.TRUE.equals(isAdmin) ? 1 : 0);
        userMapper.updateById(user);
    }

    // ==================== 管理端：审核 ====================

    /** 企业分页（管理端）：audit_status 筛选 + 名称/信用代码/联系人手机号模糊 */
    public PageResult<EnterpriseVO> page(int page, int size, Integer auditStatus, String keyword) {
        Page<UsrEnterprise> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        LambdaQueryWrapper<UsrEnterprise> qw = new LambdaQueryWrapper<UsrEnterprise>()
                .eq(auditStatus != null, UsrEnterprise::getAuditStatus, auditStatus)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(UsrEnterprise::getEnterpriseName, keyword)
                        .or().like(UsrEnterprise::getUnifiedSocialCreditCode, keyword)
                        .or().like(UsrEnterprise::getContactPhone, keyword))
                .orderByDesc(UsrEnterprise::getId);
        enterpriseMapper.selectPage(p, qw);
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    /** 企业详情（管理端） */
    public EnterpriseVO getById(Long id) {
        return toVO(requireEnterprise(id));
    }

    /** 审核：通过（1）/ 拒绝（2，必填原因；拒绝后清理该企业成员与用户关联，允许重新注册） */
    @Transactional
    public EnterpriseVO audit(Long id, EnterpriseAuditReq req) {
        UsrEnterprise enterprise = requireEnterprise(id);
        if (enterprise.getAuditStatus() == null || enterprise.getAuditStatus() != 0) {
            throw BizException.conflict("该企业已审核");
        }
        if (req.getAuditStatus() == 2 && (req.getAuditReason() == null || req.getAuditReason().isBlank())) {
            throw BizException.badRequest("拒绝时请填写审核原因");
        }
        enterprise.setAuditStatus(req.getAuditStatus());
        enterprise.setAuditReason(req.getAuditStatus() == 2 ? req.getAuditReason() : null);
        enterprise.setAuditedAt(LocalDateTime.now());
        if (req.getAuditStatus() == 1) {
            enterprise.setStatus(1);
        }
        enterpriseMapper.updateById(enterprise);

        if (req.getAuditStatus() == 2) {
            releaseEnterpriseMembers(enterprise.getId());
        }
        return toVO(enterprise);
    }

    // ==================== 内部工具 ====================

    /**
     * 当前用户作为企业管理员的企业（成员 role=1 且已接受，企业已通过审核）。
     * 供员工管理 / 会员购买等仅企业管理员可操作的能力使用。
     */
    public UsrEnterprise requireActiveEnterpriseOfAdmin() {
        Long userId = UserContext.getUserId();
        UsrEnterpriseMember member = memberMapper.selectOne(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getUserId, userId)
                .eq(UsrEnterpriseMember::getInviteStatus, INVITE_ACCEPTED)
                .eq(UsrEnterpriseMember::getRole, ROLE_ADMIN));
        if (member == null) {
            throw BizException.forbidden("仅企业管理员可操作");
        }
        UsrEnterprise enterprise = enterpriseMapper.selectById(member.getEnterpriseId());
        if (enterprise == null || enterprise.getAuditStatus() == null || enterprise.getAuditStatus() != 1) {
            throw BizException.conflict("企业未通过审核");
        }
        return enterprise;
    }

    private UsrEnterpriseMember acceptedMemberOf(Long userId) {
        List<UsrEnterpriseMember> list = memberMapper.selectList(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getUserId, userId)
                .eq(UsrEnterpriseMember::getInviteStatus, INVITE_ACCEPTED)
                .orderByDesc(UsrEnterpriseMember::getId));
        if (list.isEmpty()) {
            throw BizException.notFound("您尚未加入企业");
        }
        return list.get(0);
    }

    private UsrEnterpriseMember requireOwnInvite(Long memberId, Long userId) {
        UsrEnterpriseMember member = memberMapper.selectById(memberId);
        if (member == null || !member.getUserId().equals(userId)) {
            throw BizException.notFound("邀请不存在");
        }
        return member;
    }

    private void insertMember(Long enterpriseId, Long userId, int role, int inviteStatus) {
        UsrEnterpriseMember member = new UsrEnterpriseMember();
        member.setEnterpriseId(enterpriseId);
        member.setUserId(userId);
        member.setRole(role);
        member.setInviteStatus(inviteStatus);
        member.setInvitedAt(LocalDateTime.now());
        if (inviteStatus == INVITE_ACCEPTED) {
            member.setAcceptedAt(LocalDateTime.now());
        }
        memberMapper.insert(member);
    }

    /** 审核拒绝：清理该企业全部成员关系与用户关联（员工可另行被邀请/注册） */
    private void releaseEnterpriseMembers(Long enterpriseId) {
        List<UsrEnterpriseMember> members = memberMapper.selectList(new LambdaQueryWrapper<UsrEnterpriseMember>()
                .eq(UsrEnterpriseMember::getEnterpriseId, enterpriseId));
        for (UsrEnterpriseMember m : members) {
            memberMapper.deleteById(m.getId());
            clearUserEnterprise(m.getUserId());
        }
    }

    private void clearUserEnterprise(Long userId) {
        // 注意：updateById 默认跳过 null 字段，清空关联必须用 LambdaUpdateWrapper 显式 set null
        userMapper.update(null, new LambdaUpdateWrapper<UsrUser>()
                .eq(UsrUser::getId, userId)
                .set(UsrUser::getEnterpriseId, null)
                .set(UsrUser::getIsEnterpriseAdmin, 0)
                .set(UsrUser::getMemberLevel, 0));
    }

    /** 接受邀请时同步企业会员等级（若企业在有效期内） */
    private void syncMemberLevel(UsrUser user, UsrEnterprise enterprise) {
        if (effectiveMemberLevel(enterprise) > 0) {
            user.setMemberLevel(enterprise.getMemberLevel());
        } else {
            user.setMemberLevel(0);
        }
    }

    /** 有效会员等级：过期惰性降级为 0（不写库） */
    public static int effectiveMemberLevel(UsrEnterprise enterprise) {
        if (enterprise.getMemberLevel() == null || enterprise.getMemberLevel() == 0) {
            return 0;
        }
        if (enterprise.getMemberExpireAt() != null
                && enterprise.getMemberExpireAt().isBefore(LocalDateTime.now())) {
            return 0;
        }
        return enterprise.getMemberLevel();
    }

    private Map<Long, UsrUser> usersOf(List<UsrEnterpriseMember> members) {
        List<Long> ids = members.stream().map(UsrEnterpriseMember::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(UsrUser::getId, Function.identity()));
    }

    private UsrUser requireUser(Long id) {
        UsrUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private UsrEnterprise requireEnterprise(Long id) {
        UsrEnterprise enterprise = enterpriseMapper.selectById(id);
        if (enterprise == null) {
            throw BizException.notFound("企业不存在");
        }
        return enterprise;
    }

    private EnterpriseVO toVO(UsrEnterprise e) {
        EnterpriseVO vo = new EnterpriseVO();
        vo.setId(e.getId());
        vo.setEnterpriseName(e.getEnterpriseName());
        vo.setUnifiedSocialCreditCode(e.getUnifiedSocialCreditCode());
        vo.setBusinessLicenseUrl(e.getBusinessLicenseUrl());
        vo.setLegalPersonName(e.getLegalPersonName());
        vo.setLegalPersonIdCardFront(e.getLegalPersonIdCardFront());
        vo.setLegalPersonIdCardBack(e.getLegalPersonIdCardBack());
        vo.setContactName(e.getContactName());
        vo.setContactPhone(e.getContactPhone());
        vo.setEnterpriseType(e.getEnterpriseType());
        vo.setMemberLevel(effectiveMemberLevel(e));
        vo.setMemberExpireAt(TimeUtil.toEpochMillis(e.getMemberExpireAt()));
        vo.setAuditStatus(e.getAuditStatus());
        vo.setAuditReason(e.getAuditReason());
        vo.setAuditedAt(TimeUtil.toEpochMillis(e.getAuditedAt()));
        vo.setStatus(e.getStatus());
        vo.setCreatedAt(TimeUtil.toEpochMillis(e.getCreatedAt()));
        return vo;
    }
}
