package com.example.leaseplatform.trd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值档位（trd_recharge_tiers）：充值赠送规则配置。
 * 自增 ID（配置表，见 db/README.md 主键 ID 策略）。
 */
@Data
@TableName("trd_recharge_tiers")
public class TrdRechargeTier {

    /** 自增 ID（配置表） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 充值金额（唯一） */
    private BigDecimal rechargeAmount;

    /** 赠送金额 */
    private BigDecimal bonusAmount;

    /** 实际到账金额（充值 + 赠送） */
    private BigDecimal actualAmount;

    /** 相当于折扣（如 0.91） */
    private BigDecimal equivalentDiscount;

    /** 排序权重（升序） */
    private Integer sortOrder;

    /** 状态：0-禁用, 1-启用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
