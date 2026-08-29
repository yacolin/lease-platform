package com.example.leaseplatform.usr.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.security.LoginUser;
import com.example.leaseplatform.usr.dto.EnterpriseAuditReq;
import com.example.leaseplatform.usr.dto.EnterpriseRegisterReq;
import com.example.leaseplatform.usr.dto.EnterpriseVO;
import com.example.leaseplatform.usr.dto.InviteMemberReq;
import com.example.leaseplatform.usr.entity.UsrEnterprise;
import com.example.leaseplatform.usr.entity.UsrEnterpriseMember;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrEnterpriseMapper;
import com.example.leaseplatform.usr.mapper.UsrEnterpriseMemberMapper;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业域服务单元测试：注册 / 我的企业 / 员工管理 / 审核。
 */
@ExtendWith(MockitoExtension.class)
class EnterpriseServiceTest {

    @Mock
    private UsrEnterpriseMapper enterpriseMapper;
    @Mock
    private UsrEnterpriseMemberMapper memberMapper;
    @Mock
    private UsrUserMapper userMapper;

    private EnterpriseService service;

    @BeforeEach
    void setUp() {
        service = new EnterpriseService(enterpriseMapper, memberMapper, userMapper);
        loginAs(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId) {
        LoginUser loginUser = LoginUser.of(userId, 3);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    private EnterpriseRegisterReq registerReq() {
        EnterpriseRegisterReq req = new EnterpriseRegisterReq();
        req.setEnterpriseName("测试企业");
        req.setUnifiedSocialCreditCode("91110000TEST");
        req.setBusinessLicenseUrl("https://x/license.png");
        req.setLegalPersonName("张三");
        req.setLegalPersonIdCardFront("https://x/id1.png");
        req.setLegalPersonIdCardBack("https://x/id2.png");
        req.setContactName("张三");
        req.setContactPhone("13800138000");
        return req;
    }

    private UsrEnterprise enterprise(Long id, int auditStatus) {
        UsrEnterprise e = new UsrEnterprise();
        e.setId(id);
        e.setEnterpriseName("测试企业");
        e.setAuditStatus(auditStatus);
        e.setStatus(1);
        e.setMemberLevel(0);
        return e;
    }

    private UsrEnterpriseMember member(Long id, Long enterpriseId, Long userId, int role, int inviteStatus) {
        UsrEnterpriseMember m = new UsrEnterpriseMember();
        m.setId(id);
        m.setEnterpriseId(enterpriseId);
        m.setUserId(userId);
        m.setRole(role);
        m.setInviteStatus(inviteStatus);
        return m;
    }

    private UsrUser user(Long id, String phone) {
        UsrUser u = new UsrUser();
        u.setId(id);
        u.setNickname("用户" + id);
        u.setPhone(phone);
        return u;
    }

    // ==================== 注册 ====================

    @Test
    void register_shouldCreateEnterpriseAndAdminMember() {
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(enterpriseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(enterpriseMapper.insert(any(UsrEnterprise.class))).thenAnswer(inv -> {
            ((UsrEnterprise) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(memberMapper.insert(any(UsrEnterpriseMember.class))).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user(1L, "13800138000"));

        EnterpriseVO vo = service.register(registerReq());

        assertThat(vo.getId()).isEqualTo(100L);
        assertThat(vo.getAuditStatus()).isZero();
        ArgumentCaptor<UsrEnterpriseMember> captor = ArgumentCaptor.forClass(UsrEnterpriseMember.class);
        verify(memberMapper).insert(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(1); // 注册人 = 企业管理员
        assertThat(captor.getValue().getInviteStatus()).isEqualTo(1); // 已接受
        verify(userMapper).updateById(any(UsrUser.class));
    }

    @Test
    void register_alreadyJoined_shouldConflict() {
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.register(registerReq()))
                .isInstanceOf(BizException.class)
                .hasMessage("您已加入企业，不能重复注册");
        verify(enterpriseMapper, never()).insert(any(UsrEnterprise.class));
    }

    @Test
    void register_duplicateCreditCode_shouldConflict() {
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(enterpriseMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.register(registerReq()))
                .isInstanceOf(BizException.class)
                .hasMessage("该统一社会信用代码已注册");
    }

    // ==================== 我的企业 / 员工 ====================

    @Test
    void myEnterprise_shouldReturnOwn() {
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(member(1L, 100L, 1L, 1, 1)));
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 0));

        EnterpriseVO vo = service.myEnterprise();

        assertThat(vo.getId()).isEqualTo(100L);
        assertThat(vo.getAuditStatus()).isZero();
    }

    @Test
    void myEnterprise_notJoined_should404() {
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.myEnterprise())
                .isInstanceOf(BizException.class)
                .hasMessage("您尚未加入企业");
    }

    @Test
    void members_shouldRequireActiveAdminEnterprise() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.members())
                .isInstanceOf(BizException.class)
                .hasMessage("仅企业管理员可操作");
    }

    @Test
    void invite_newUser_shouldCreatePendingMember() {
        // 第 1 次 selectOne：当前用户的企业管理员关系；第 2 次：目标用户无既有关系
        when(memberMapper.selectOne(any(Wrapper.class)))
                .thenReturn(member(1L, 100L, 1L, 1, 1), null);
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 1));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(2L, "13900139000"));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        InviteMemberReq req = new InviteMemberReq();
        req.setPhone("13900139000");
        service.invite(req);

        ArgumentCaptor<UsrEnterpriseMember> captor = ArgumentCaptor.forClass(UsrEnterpriseMember.class);
        verify(memberMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(2L);
        assertThat(captor.getValue().getInviteStatus()).isZero();
        assertThat(captor.getValue().getRole()).isZero();
    }

    @Test
    void invite_phoneNotRegistered_should400() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member(1L, 100L, 1L, 1, 1));
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 1));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        InviteMemberReq req = new InviteMemberReq();
        req.setPhone("13900139000");
        assertThatThrownBy(() -> service.invite(req))
                .isInstanceOf(BizException.class)
                .hasMessage("该手机号未注册小程序用户");
    }

    // ==================== 邀请处理 ====================

    @Test
    void acceptInvite_shouldJoinEnterprise() {
        UsrEnterpriseMember pending = member(10L, 100L, 1L, 0, 0);
        when(memberMapper.selectById(10L)).thenReturn(pending);
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 1));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(userMapper.selectById(1L)).thenReturn(user(1L, "13800138000"));

        service.acceptInvite(10L);

        assertThat(pending.getInviteStatus()).isEqualTo(1);
        verify(memberMapper).updateById(pending);
        ArgumentCaptor<UsrUser> captor = ArgumentCaptor.forClass(UsrUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnterpriseId()).isEqualTo(100L);
        assertThat(captor.getValue().getIsEnterpriseAdmin()).isZero();
    }

    @Test
    void acceptInvite_enterpriseNotAudited_shouldConflict() {
        UsrEnterpriseMember pending = member(10L, 100L, 1L, 0, 0);
        when(memberMapper.selectById(10L)).thenReturn(pending);
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 0));

        assertThatThrownBy(() -> service.acceptInvite(10L))
                .isInstanceOf(BizException.class)
                .hasMessage("企业尚未通过审核，无法接受邀请");
    }

    @Test
    void acceptInvite_notOwnInvite_should404() {
        UsrEnterpriseMember other = member(10L, 100L, 99L, 0, 0);
        when(memberMapper.selectById(10L)).thenReturn(other);

        assertThatThrownBy(() -> service.acceptInvite(10L))
                .isInstanceOf(BizException.class)
                .hasMessage("邀请不存在");
    }

    @Test
    void removeMember_self_should400() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member(1L, 100L, 1L, 1, 1));
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 1));

        assertThatThrownBy(() -> service.removeMember(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("不能移除自己");
    }

    @Test
    void removeMember_admin_shouldConflict() {
        // 第 1 次：当前用户管理员关系；第 2 次：目标员工（角色为管理员）
        when(memberMapper.selectOne(any(Wrapper.class)))
                .thenReturn(member(1L, 100L, 1L, 1, 1), member(2L, 100L, 2L, 1, 1));
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 1));

        assertThatThrownBy(() -> service.removeMember(2L))
                .isInstanceOf(BizException.class)
                .hasMessage("请先取消该员工的企业管理员身份");
    }

    @Test
    void removeMember_employee_shouldDeleteAndClearUser() {
        UsrEnterpriseMember emp = member(2L, 100L, 2L, 0, 1);
        // 第 1 次：当前用户管理员关系；第 2 次：目标员工
        when(memberMapper.selectOne(any(Wrapper.class)))
                .thenReturn(member(1L, 100L, 1L, 1, 1), emp);
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 1));

        service.removeMember(2L);

        verify(memberMapper).deleteById(2L);
        verify(userMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void setAdmin_shouldUpdateRoleAndFlag() {
        UsrEnterpriseMember emp = member(2L, 100L, 2L, 0, 1);
        // 第 1 次：当前用户管理员关系；第 2 次：目标员工
        when(memberMapper.selectOne(any(Wrapper.class)))
                .thenReturn(member(1L, 100L, 1L, 1, 1), emp);
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 1));
        when(userMapper.selectById(2L)).thenReturn(user(2L, "13900139000"));

        service.setAdmin(2L, true);

        assertThat(emp.getRole()).isEqualTo(1);
        verify(userMapper).updateById(any(UsrUser.class));
    }

    // ==================== 审核 ====================

    @Test
    void audit_pass_shouldSetAudited() {
        UsrEnterprise e = enterprise(100L, 0);
        when(enterpriseMapper.selectById(100L)).thenReturn(e);

        EnterpriseAuditReq req = new EnterpriseAuditReq();
        req.setAuditStatus(1);
        EnterpriseVO vo = service.audit(100L, req);

        assertThat(vo.getAuditStatus()).isEqualTo(1);
        verify(enterpriseMapper).updateById(e);
    }

    @Test
    void audit_reject_withoutReason_should400() {
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 0));

        EnterpriseAuditReq req = new EnterpriseAuditReq();
        req.setAuditStatus(2);
        assertThatThrownBy(() -> service.audit(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("拒绝时请填写审核原因");
    }

    @Test
    void audit_reject_shouldReleaseMembers() {
        UsrEnterprise e = enterprise(100L, 0);
        when(enterpriseMapper.selectById(100L)).thenReturn(e);
        when(memberMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(member(1L, 100L, 1L, 1, 1), member(2L, 100L, 2L, 0, 1)));

        EnterpriseAuditReq req = new EnterpriseAuditReq();
        req.setAuditStatus(2);
        req.setAuditReason("资料不完整");
        EnterpriseVO vo = service.audit(100L, req);

        assertThat(vo.getAuditStatus()).isEqualTo(2);
        assertThat(vo.getAuditReason()).isEqualTo("资料不完整");
        verify(memberMapper, org.mockito.Mockito.times(2)).deleteById(any(Long.class));
        verify(userMapper, org.mockito.Mockito.times(2)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void audit_alreadyAudited_shouldConflict() {
        when(enterpriseMapper.selectById(100L)).thenReturn(enterprise(100L, 1));

        EnterpriseAuditReq req = new EnterpriseAuditReq();
        req.setAuditStatus(1);
        assertThatThrownBy(() -> service.audit(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("该企业已审核");
    }

    @Test
    void page_shouldReturnPagedResult() {
        when(enterpriseMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<UsrEnterprise> p = inv.getArgument(0);
            p.setRecords(List.of(enterprise(100L, 0)));
            p.setTotal(1);
            return p;
        });

        PageResult<EnterpriseVO> result = service.page(1, 10, null, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getId()).isEqualTo(100L);
    }
}
