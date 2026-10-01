package com.example.leaseplatform.trd.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.trd.dto.RechargeTierReq;
import com.example.leaseplatform.trd.dto.RechargeTierVO;
import com.example.leaseplatform.trd.entity.TrdRechargeTier;
import com.example.leaseplatform.trd.mapper.TrdRechargeTierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 充值档位服务：公开列表（仅启用）+ 管理端 CRUD（双轨，见 docs/接口规范.md）。
 */
@Service
@RequiredArgsConstructor
public class RechargeTierService {

    private final TrdRechargeTierMapper tierMapper;

    /** 公开列表：仅启用，按 sort_order 升序 */
    public List<RechargeTierVO> publicList() {
        return tierMapper.selectList(new LambdaQueryWrapper<TrdRechargeTier>()
                        .eq(TrdRechargeTier::getStatus, 1)
                        .orderByAsc(TrdRechargeTier::getSortOrder)
                        .orderByAsc(TrdRechargeTier::getId))
                .stream().map(this::toVO).toList();
    }

    /** 管理端分页 */
    public PageResult<RechargeTierVO> page(int page, int size, Integer status) {
        Page<TrdRechargeTier> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        tierMapper.selectPage(p, new LambdaQueryWrapper<TrdRechargeTier>()
                .eq(status != null, TrdRechargeTier::getStatus, status)
                .orderByAsc(TrdRechargeTier::getSortOrder)
                .orderByAsc(TrdRechargeTier::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    public RechargeTierVO getById(Long id) {
        return toVO(require(id));
    }

    @Transactional
    public RechargeTierVO create(RechargeTierReq req) {
        checkDuplicateAmount(req.getRechargeAmount(), null);
        TrdRechargeTier tier = new TrdRechargeTier();
        apply(tier, req);
        tierMapper.insert(tier);
        return toVO(tier);
    }

    @Transactional
    public RechargeTierVO update(Long id, RechargeTierReq req) {
        TrdRechargeTier tier = require(id);
        checkDuplicateAmount(req.getRechargeAmount(), id);
        apply(tier, req);
        tierMapper.updateById(tier);
        return toVO(tier);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        tierMapper.deleteById(id);
    }

    private void apply(TrdRechargeTier tier, RechargeTierReq req) {
        tier.setRechargeAmount(req.getRechargeAmount());
        tier.setBonusAmount(req.getBonusAmount());
        // 实际到账 = 充值 + 赠送（整数分）
        tier.setActualAmount(req.getRechargeAmount() + req.getBonusAmount());
        tier.setEquivalentDiscount(req.getEquivalentDiscount());
        tier.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        tier.setStatus(req.getStatus() == null ? 1 : req.getStatus());
    }

    /** 充值金额唯一（uk_recharge_amount），预检查给出友好错误 */
    private void checkDuplicateAmount(long amount, Long excludeId) {
        Long count = tierMapper.selectCount(new LambdaQueryWrapper<TrdRechargeTier>()
                .eq(TrdRechargeTier::getRechargeAmount, amount)
                .ne(excludeId != null, TrdRechargeTier::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.conflict("该充值金额档位已存在");
        }
    }

    private TrdRechargeTier require(Long id) {
        TrdRechargeTier tier = tierMapper.selectById(id);
        if (tier == null) {
            throw BizException.notFound("充值档位不存在");
        }
        return tier;
    }

    private RechargeTierVO toVO(TrdRechargeTier tier) {
        RechargeTierVO vo = new RechargeTierVO();
        vo.setId(tier.getId());
        vo.setRechargeAmount(tier.getRechargeAmount());
        vo.setBonusAmount(tier.getBonusAmount());
        vo.setActualAmount(tier.getActualAmount());
        vo.setEquivalentDiscount(tier.getEquivalentDiscount());
        vo.setSortOrder(tier.getSortOrder());
        vo.setStatus(tier.getStatus());
        vo.setCreatedAt(TimeUtil.toEpochMillis(tier.getCreatedAt()));
        return vo;
    }
}
