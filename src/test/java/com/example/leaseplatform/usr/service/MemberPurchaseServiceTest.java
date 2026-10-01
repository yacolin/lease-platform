package com.example.leaseplatform.usr.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.cache.MultiLevelCache;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.security.LoginUser;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员购买服务单元测试：等级列表 / 下单 / mock 支付生效 / 记录。
 */
@ExtendWith(MockitoExtension.class)
class MemberPurchaseServiceTest {

    @Mock
    private UsrMemberLevelMapper levelMapper;
    @Mock
    private UsrMemberPurchaseMapper purchaseMapper;
    @Mock
    private UsrEnterpriseMapper enterpriseMapper;
    @Mock
    private UsrEnterpriseMemberMapper memberMapper;
    @Mock
    private UsrUserMapper userMapper;
    @Mock
    private EnterpriseService enterpriseService;
    @Mock
    private com.example.leaseplatform.trd.service.PaymentService paymentService;

    @Mock
    private MultiLevelCache cache;

    private MemberPurchaseService service;

    @BeforeEach
    void setUp() {
        service = new MemberPurchaseService(levelMapper, cache, purchaseMapper, enterpriseMapper,
                memberMapper, userMapper, enterpriseService, paymentService);
        // MultiLevelCache 为透传 mock：直接调用 loader，使既有断言仍校验真实查询与映射逻辑
        // 用 lenient：只有部分用例会走到缓存读取路径
        lenient().when(cache.getList(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(4)).get());
        LoginUser loginUser = LoginUser.of(1L, 3);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UsrEnterprise activeEnterprise(Long id) {
        UsrEnterprise e = new UsrEnterprise();
        e.setId(id);
        e.setMemberLevel(0);
        return e;
    }

    private UsrMemberLevel vipLevel() {
        UsrMemberLevel l = new UsrMemberLevel();
        l.setId(2L);
        l.setLevelCode("VIP");
        l.setLevelName("VIP版");
        l.setPrice(500000L);
        l.setStatus(1);
        return l;
    }

    private UsrMemberPurchase purchase(Long id, Long enterpriseId, Long levelId) {
        UsrMemberPurchase p = new UsrMemberPurchase();
        p.setId(id);
        p.setEnterpriseId(enterpriseId);
        p.setMemberLevelId(levelId);
        p.setPaymentStatus(0);
        p.setOriginalPrice(500000L);
        p.setStartDate(java.time.LocalDate.now());
        p.setEndDate(java.time.LocalDate.now().plusYears(1));
        return p;
    }

    @Test
    void publicLevels_shouldReturnEnabledOnly() {
        when(levelMapper.selectList(any(Wrapper.class))).thenReturn(List.of(vipLevel()));

        List<MemberLevelVO> list = service.publicLevels();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getLevelCode()).isEqualTo("VIP");
    }

    @Test
    void createPurchase_shouldCreatePendingOrder() {
        UsrEnterprise e = activeEnterprise(100L);
        when(enterpriseService.requireActiveEnterpriseOfAdmin()).thenReturn(e);
        when(levelMapper.selectById(2L)).thenReturn(vipLevel());
        when(purchaseMapper.insert(any(UsrMemberPurchase.class))).thenAnswer(inv -> {
            ((UsrMemberPurchase) inv.getArgument(0)).setId(500L);
            return 1;
        });

        MemberPurchaseReq req = new MemberPurchaseReq();
        req.setMemberLevelId(2L);
        MemberPurchaseVO vo = service.createPurchase(req);

        assertThat(vo.getId()).isEqualTo(500L);
        assertThat(vo.getMemberLevelCode()).isEqualTo("VIP");
        assertThat(vo.getPaymentStatus()).isZero();
        ArgumentCaptor<UsrMemberPurchase> captor = ArgumentCaptor.forClass(UsrMemberPurchase.class);
        verify(purchaseMapper).insert(captor.capture());
        assertThat(captor.getValue().getPurchaseNo()).startsWith("MP");
        assertThat(captor.getValue().getOutTradeNo()).startsWith("OT");
        assertThat(captor.getValue().getEndDate()).isEqualTo(captor.getValue().getStartDate().plusYears(1));
    }

    @Test
    void createPurchase_levelDisabled_should400() {
        when(enterpriseService.requireActiveEnterpriseOfAdmin()).thenReturn(activeEnterprise(100L));
        UsrMemberLevel disabled = vipLevel();
        disabled.setStatus(0);
        when(levelMapper.selectById(2L)).thenReturn(disabled);

        MemberPurchaseReq req = new MemberPurchaseReq();
        req.setMemberLevelId(2L);
        assertThatThrownBy(() -> service.createPurchase(req))
                .isInstanceOf(BizException.class)
                .hasMessage("该会员等级未启用");
        verify(purchaseMapper, never()).insert(any(UsrMemberPurchase.class));
    }

    @Test
    void mockPay_shouldActivateMembershipAndSyncEmployees() {
        UsrEnterprise e = activeEnterprise(100L);
        when(enterpriseService.requireActiveEnterpriseOfAdmin()).thenReturn(e);
        UsrMemberPurchase p = purchase(500L, 100L, 2L);
        when(purchaseMapper.selectById(500L)).thenReturn(p);
        when(levelMapper.selectById(2L)).thenReturn(vipLevel());
        when(memberMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(empMember(2L), empMember(3L)));

        MemberPurchaseVO vo = service.mockPay(500L);

        assertThat(vo.getPaymentStatus()).isEqualTo(1);
        // 企业等级 + 到期时间
        ArgumentCaptor<UsrEnterprise> eCaptor = ArgumentCaptor.forClass(UsrEnterprise.class);
        verify(enterpriseMapper).updateById(eCaptor.capture());
        assertThat(eCaptor.getValue().getMemberLevel()).isEqualTo(2); // VIP → 2
        assertThat(eCaptor.getValue().getMemberExpireAt()).isNotNull();
        // 员工同步：一条批量 UPDATE 设置 member_level
        // （原实现逐个 selectById + updateById，2M 条 SQL 且整行覆盖）
        verify(userMapper, times(1)).update(isNull(), any(Wrapper.class));
        verify(userMapper, never()).updateById(any(UsrUser.class));
    }

    @Test
    void mockPay_notOwnPurchase_should404() {
        UsrEnterprise e = activeEnterprise(100L);
        when(enterpriseService.requireActiveEnterpriseOfAdmin()).thenReturn(e);
        UsrMemberPurchase other = purchase(500L, 999L, 2L);
        when(purchaseMapper.selectById(500L)).thenReturn(other);

        assertThatThrownBy(() -> service.mockPay(500L))
                .isInstanceOf(BizException.class)
                .hasMessage("购买记录不存在");
    }

    @Test
    void mockPay_alreadyPaid_shouldConflict() {
        UsrEnterprise e = activeEnterprise(100L);
        when(enterpriseService.requireActiveEnterpriseOfAdmin()).thenReturn(e);
        UsrMemberPurchase p = purchase(500L, 100L, 2L);
        p.setPaymentStatus(1);
        when(purchaseMapper.selectById(500L)).thenReturn(p);

        assertThatThrownBy(() -> service.mockPay(500L))
                .isInstanceOf(BizException.class)
                .hasMessage("该订单已支付");
    }

    @Test
    void myPurchases_shouldReturnPaged() {
        when(enterpriseService.requireActiveEnterpriseOfAdmin()).thenReturn(activeEnterprise(100L));
        when(purchaseMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<UsrMemberPurchase> p = inv.getArgument(0);
            UsrMemberPurchase pr = purchase(500L, 100L, 2L);
            pr.setPaymentStatus(1);
            p.setRecords(List.of(pr));
            p.setTotal(1);
            return p;
        });
        // 分页结果批量取等级（一次 IN 查询，替代原先逐行 selectById）
        when(levelMapper.selectBatchIds(any())).thenReturn(List.of(vipLevel()));

        PageResult<MemberPurchaseVO> result = service.myPurchases(1, 10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getMemberLevelName()).isEqualTo("VIP版");
    }

    @Test
    void mapLevelCode_shouldMapCodes() {
        assertThat(MemberPurchaseService.mapLevelCode("BASIC")).isEqualTo(1);
        assertThat(MemberPurchaseService.mapLevelCode("VIP")).isEqualTo(2);
        assertThat(MemberPurchaseService.mapLevelCode("svip")).isEqualTo(3);
        assertThat(MemberPurchaseService.mapLevelCode(null)).isZero();
    }

    private UsrEnterpriseMember empMember(Long userId) {
        UsrEnterpriseMember m = new UsrEnterpriseMember();
        m.setUserId(userId);
        m.setEnterpriseId(100L);
        m.setInviteStatus(1);
        return m;
    }

    private UsrUser user(Long id) {
        UsrUser u = new UsrUser();
        u.setId(id);
        return u;
    }
}
