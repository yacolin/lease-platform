-- ============================ 交易域 (trd_) ============================
-- trd_recharge_tiers 充值档位 / trd_recharge_records 充值记录 /
-- trd_balance_transactions 余额流水
-- 本文件可重复执行（先 DROP 再 CREATE）；删除或调整本域表时直接修改本文件
--
-- 表结构遵照 1.0 版本共享对话的 MySQL 设计（数据库 lease_db）；
-- 表名按 beauty_salon 约定加域前缀 `trd_`；跨域关系由服务层保证，本库暂不加外键约束。
-- 约定：主键 BIGINT UNSIGNED AUTO_INCREMENT；金额 DECIMAL(10,2)（元）；
-- 纯流水表（trd_balance_transactions）只保留 created_at。

-- 反向依赖顺序删除（trd_balance_transactions → trd_recharge_records → trd_recharge_tiers）
DROP TABLE IF EXISTS `trd_balance_transactions`;
DROP TABLE IF EXISTS `trd_recharge_records`;
DROP TABLE IF EXISTS `trd_recharge_tiers`;

-- 充值档位表：充值赠送规则配置
CREATE TABLE `trd_recharge_tiers` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '档位ID',
  `recharge_amount` DECIMAL(10,2) NOT NULL COMMENT '充值金额',
  `bonus_amount` DECIMAL(10,2) NOT NULL COMMENT '赠送金额',
  `actual_amount` DECIMAL(10,2) NOT NULL COMMENT '实际到账金额（充值+赠送）',
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
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `tier_id` BIGINT UNSIGNED NOT NULL COMMENT '充值档位ID',
  `recharge_amount` DECIMAL(10,2) NOT NULL COMMENT '充值金额',
  `bonus_amount` DECIMAL(10,2) NOT NULL COMMENT '赠送金额',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '到账总金额',
  `payment_method` TINYINT NOT NULL DEFAULT 1 COMMENT '支付方式：1-微信支付, 2-余额支付',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '微信支付交易号',
  `out_trade_no` VARCHAR(64) NOT NULL COMMENT '商户订单号',
  `payment_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态：0-待支付, 1-支付成功, 2-支付失败, 3-已退款',
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
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `transaction_type` TINYINT NOT NULL COMMENT '类型：1-充值, 2-消费, 3-退款, 4-赠送, 5-调整',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '变动金额（正数增加，负数减少）',
  `balance_before` DECIMAL(10,2) NOT NULL COMMENT '变动前余额',
  `balance_after` DECIMAL(10,2) NOT NULL COMMENT '变动后余额',
  `gift_balance_before` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '变动前赠送余额',
  `gift_balance_after` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '变动后赠送余额',
  `related_order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联订单ID（订单表）',
  `related_recharge_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联充值记录ID',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_related_order_id` (`related_order_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='余额流水表';

-- ---------- 种子数据（充值档位） ----------
INSERT INTO `trd_recharge_tiers` (`recharge_amount`, `bonus_amount`, `actual_amount`, `equivalent_discount`, `sort_order`) VALUES
  (200.00,  20.00,  220.00,  0.91, 1),
  (500.00,  60.00,  560.00,  0.89, 2),
  (1000.00, 150.00, 1150.00, 0.87, 3),
  (2000.00, 400.00, 2400.00, 0.83, 4);
