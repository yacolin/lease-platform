-- ============================ 交易域 (trd_) ============================
-- trd_recharge_tiers 充值档位 / trd_recharge_records 充值记录 /
-- trd_balance_transactions 余额流水 / trd_payments 支付单（1.2）/
-- trd_refunds 退款单（1.2）/ acct_accounts 用户账户（1.5）
-- 本文件可重复执行（先 DROP 再 CREATE）；删除或调整本域表时直接修改本文件
--
-- 表结构遵照 1.0 版本共享对话的 MySQL 设计（数据库 lease_db）；
-- 表名按 beauty_salon 约定加域前缀 `trd_`；跨域关系由服务层保证，本库暂不加外键约束。
-- 约定：业务表主键 BIGINT UNSIGNED（雪花，无自增，见 db/README.md 主键 ID 策略；
-- 充值档位配置 trd_recharge_tiers 自增）；金额 BIGINT（分，最小单位整数，
-- 全链路统一以「分」为单位，与微信支付对齐；折扣率 equivalent_discount 仍为 DECIMAL）；
-- 纯流水表（trd_balance_transactions）只保留 created_at；
-- 支付单/退款单（1.2）：编号与外部交易号唯一索引，幂等键唯一（防重复退款）；
-- 账户（1.5）：acct_accounts 为余额唯一事实源（可用/赠送/冻结），usr_users 不再持有余额。

-- 反向依赖顺序删除（acct_accounts → trd_balance_transactions → trd_refunds → trd_payments →
-- trd_recharge_records → trd_recharge_tiers）
DROP TABLE IF EXISTS `acct_accounts`;
DROP TABLE IF EXISTS `trd_balance_transactions`;
DROP TABLE IF EXISTS `trd_refunds`;
DROP TABLE IF EXISTS `trd_payments`;
DROP TABLE IF EXISTS `trd_recharge_records`;
DROP TABLE IF EXISTS `trd_recharge_tiers`;

-- 充值档位表：充值赠送规则配置
CREATE TABLE `trd_recharge_tiers` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '档位ID',
  `recharge_amount` BIGINT NOT NULL COMMENT '充值金额（分）',
  `bonus_amount` BIGINT NOT NULL COMMENT '赠送金额（分）',
  `actual_amount` BIGINT NOT NULL COMMENT '实际到账金额（分，充值+赠送）',
  `equivalent_discount` DECIMAL(3,2) NOT NULL COMMENT '相当于折扣（如0.91）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recharge_amount` (`recharge_amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值档位表';

-- 充值记录表：用户充值流水
CREATE TABLE `trd_recharge_records` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '记录ID（雪花）',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `tier_id` BIGINT UNSIGNED NOT NULL COMMENT '充值档位ID',
  `recharge_amount` BIGINT NOT NULL COMMENT '充值金额（分）',
  `bonus_amount` BIGINT NOT NULL COMMENT '赠送金额（分）',
  `total_amount` BIGINT NOT NULL COMMENT '到账总金额（分）',
  `payment_method` TINYINT NOT NULL DEFAULT 1 COMMENT '支付方式：1-微信支付, 2-余额支付',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '微信支付交易号',
  `out_trade_no` VARCHAR(64) NOT NULL COMMENT '商户订单号',
  `payment_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态：0-待支付, 1-支付成功, 2-支付失败, 3-已退款',
  `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间',
  `refund_reason` VARCHAR(255) DEFAULT NULL COMMENT '退款原因',
  `refunded_at` DATETIME DEFAULT NULL COMMENT '退款时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_transaction_id` (`transaction_id`),
  KEY `idx_out_trade_no` (`out_trade_no`),
  KEY `idx_payment_status` (`payment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值记录表';

-- 余额流水表：所有余额变动明细（充值、消费、退款）
CREATE TABLE `trd_balance_transactions` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '流水ID（雪花）',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `transaction_type` TINYINT NOT NULL COMMENT '类型：1-充值, 2-消费, 3-退款, 4-赠送, 5-调整',
  `amount` BIGINT NOT NULL COMMENT '变动金额（分，正数增加，负数减少）',
  `balance_before` BIGINT NOT NULL COMMENT '变动前余额（分）',
  `balance_after` BIGINT NOT NULL COMMENT '变动后余额（分）',
  `gift_balance_before` BIGINT NOT NULL DEFAULT 0 COMMENT '变动前赠送余额（分）',
  `gift_balance_after` BIGINT NOT NULL DEFAULT 0 COMMENT '变动后赠送余额（分）',
  `frozen_balance_before` BIGINT NOT NULL DEFAULT 0 COMMENT '变动前冻结余额（分，1.5）',
  `frozen_balance_after` BIGINT NOT NULL DEFAULT 0 COMMENT '变动后冻结余额（分，1.5）',
  `related_order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联订单ID（订单表）',
  `related_recharge_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联充值记录ID',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_related_order_id` (`related_order_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='余额流水表';

-- 支付单表（1.2 交易可靠性）：统一支付事实，与业务单（biz_type+biz_id）解耦
CREATE TABLE `trd_payments` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '支付单ID（雪花）',
  `payment_no` VARCHAR(32) NOT NULL COMMENT '支付单编号',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '支付用户ID',
  `biz_type` TINYINT NOT NULL COMMENT '业务类型：1-充值, 2-咖啡订单, 3-正餐预订, 4-会员购买',
  `biz_id` BIGINT UNSIGNED NOT NULL COMMENT '业务单ID（充值记录/订单/预订/购买记录）',
  `amount` BIGINT NOT NULL COMMENT '支付金额（分）',
  `payment_method` TINYINT NOT NULL COMMENT '支付方式：1-余额支付, 2-微信支付',
  `payment_channel` TINYINT NOT NULL COMMENT '支付渠道：1-微信JSAPI, 2-余额, 3-mock直充',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态：0-待支付, 1-成功, 2-失败, 3-部分退款, 4-已退款',
  `out_trade_no` VARCHAR(64) NOT NULL COMMENT '商户订单号',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '支付渠道交易号',
  `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间',
  `expired_at` DATETIME DEFAULT NULL COMMENT '待支付过期时间（超时自动关闭）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  UNIQUE KEY `uk_out_trade_no` (`out_trade_no`),
  UNIQUE KEY `uk_transaction_id` (`transaction_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付单表';

-- 退款单表（1.2 交易可靠性）：全额/部分/多次退款，关联支付单，幂等键防重复
CREATE TABLE `trd_refunds` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '退款单ID（雪花）',
  `refund_no` VARCHAR(32) NOT NULL COMMENT '退款单编号',
  `payment_id` BIGINT UNSIGNED NOT NULL COMMENT '关联支付单ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '退款用户ID',
  `biz_type` TINYINT NOT NULL COMMENT '业务类型（同 trd_payments）',
  `biz_id` BIGINT UNSIGNED NOT NULL COMMENT '业务单ID',
  `refund_amount` BIGINT NOT NULL COMMENT '退款金额（分）',
  `refund_method` TINYINT NOT NULL COMMENT '退款方式：1-原路余额, 2-原路微信',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '退款状态：0-处理中, 1-成功, 2-失败',
  `idempotency_key` VARCHAR(128) DEFAULT NULL COMMENT '幂等键（防重复退款，业务取消/商家退款各一次）',
  `refund_reason` VARCHAR(255) DEFAULT NULL COMMENT '退款原因',
  `refunded_at` DATETIME DEFAULT NULL COMMENT '退款完成时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
  KEY `idx_payment_id` (`payment_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款单表';

-- 用户账户表（1.5 账户/钱包）：余额从 usr_users 剥离，账户=当前状态（流水=历史事实）
-- 主键 = 用户 ID（1:1 账户，无自增）
CREATE TABLE `acct_accounts` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '账户ID（= 用户ID，1:1 账户无自增）',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `available_balance` BIGINT NOT NULL DEFAULT 0 COMMENT '可用余额（分）',
  `gift_balance` BIGINT NOT NULL DEFAULT 0 COMMENT '赠送余额（分）',
  `frozen_balance` BIGINT NOT NULL DEFAULT 0 COMMENT '冻结余额（分，押金/预授权/待结算/退款处理中）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-冻结, 1-正常',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户表';
