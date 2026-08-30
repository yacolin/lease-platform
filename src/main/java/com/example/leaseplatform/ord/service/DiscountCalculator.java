package com.example.leaseplatform.ord.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.leaseplatform.trd.entity.TrdRechargeRecord;
import com.example.leaseplatform.trd.entity.TrdRechargeTier;
import com.example.leaseplatform.trd.mapper.TrdRechargeRecordMapper;
import com.example.leaseplatform.trd.mapper.TrdRechargeTierMapper;
import com.example.leaseplatform.usr.entity.UsrMemberLevel;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrMemberLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 折扣计算（咖啡点单 / 正餐预订共用）：
 * 应付 = 原价 × 会员折扣率 × 充值折扣率（叠加）。
 * - memberDiscountRate：usr_member_levels.discount_rate（按 member_level 数字 → BASIC/VIP/SVIP），非会员 1.0；
 * - rechargeDiscountRate：最近一次成功充值档位的 equivalent_discount，未充值 1.0。
 */
@Component
@RequiredArgsConstructor
public class DiscountCalculator {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final UsrMemberLevelMapper memberLevelMapper;
    private final TrdRechargeTierMapper rechargeTierMapper;
    private final TrdRechargeRecordMapper rechargeRecordMapper;

    /** 会员折扣率 */
    public BigDecimal memberDiscountRate(UsrUser user) {
        String levelCode = switch (user.getMemberLevel() == null ? 0 : user.getMemberLevel()) {
            case 1 -> "BASIC";
            case 2 -> "VIP";
            case 3 -> "SVIP";
            default -> null;
        };
        if (levelCode == null) {
            return ONE;
        }
        UsrMemberLevel level = memberLevelMapper.selectOne(new LambdaQueryWrapper<UsrMemberLevel>()
                .eq(UsrMemberLevel::getLevelCode, levelCode)
                .eq(UsrMemberLevel::getStatus, 1));
        return level == null || level.getDiscountRate() == null ? ONE : level.getDiscountRate();
    }

    /** 充值折扣率（最近一次成功充值档位） */
    public BigDecimal rechargeDiscountRate(Long userId) {
        TrdRechargeRecord latest = rechargeRecordMapper.selectOne(new LambdaQueryWrapper<TrdRechargeRecord>()
                .eq(TrdRechargeRecord::getUserId, userId)
                .eq(TrdRechargeRecord::getPaymentStatus, 1)
                .orderByDesc(TrdRechargeRecord::getId)
                .last("LIMIT 1"));
        if (latest == null) {
            return ONE;
        }
        TrdRechargeTier tier = rechargeTierMapper.selectById(latest.getTierId());
        return tier == null || tier.getEquivalentDiscount() == null
                ? ONE : tier.getEquivalentDiscount();
    }
}
