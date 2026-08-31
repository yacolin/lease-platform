-- ============================================================================
-- V6__trade_reliability：1.2 交易可靠性与订单闭环（roadmap 1.2）
--   1. trd_payments  支付单：统一支付事实（充值/订单/正餐预订/会员购买），
--      与业务单（biz_type + biz_id）解耦，支付单状态与业务单状态分离；
--   2. trd_refunds   退款单：支持全额/部分/多次退款，退款必须关联支付单，
--      退款总额不允许超过支付金额（服务层校验 + 幂等键防重复）；
--   3. ord_order_status_history 订单状态历史：咖啡订单/正餐预订状态流转全量留痕
--      （待支付→已支付→制作中→已完成 / 取消 / 退款），业务事实可追踪；
--   4. sys_idempotency 幂等记录：微信回调/充值回调/退款回调等外部重复通知去重，
--      配合各业务表状态乐观更新实现"重复请求不产生重复资金变动"。
-- 与 db/05_trd.sql、db/03_ord.sql、db/06_sys.sql、db/migrations/006_trade_reliability.sql
-- 最终形态保持一致（Flyway 整文件执行，无 +migrate Down 回滚段）。
-- 约定：新增表主键为雪花 ID（BIGINT UNSIGNED，无自增）；金额 BIGINT（分）；
-- 编号/幂等键/外部交易号全部唯一索引（roadmap 1.2.6 数据库约束增强）。
-- ============================================================================

-- 1. 支付单表
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

-- 2. 退款单表
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

-- 3. 订单状态历史表
CREATE TABLE `ord_order_status_history` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '记录ID（雪花）',
  `order_id` BIGINT UNSIGNED NOT NULL COMMENT '业务单ID（咖啡订单 ord_orders.id 或 正餐预订 ord_meal_reservations.id）',
  `biz_type` TINYINT NOT NULL DEFAULT 1 COMMENT '业务类型：1-咖啡订单, 2-正餐预订',
  `from_status` TINYINT DEFAULT NULL COMMENT '变更前状态（初始状态为 NULL）',
  `to_status` TINYINT NOT NULL COMMENT '变更后状态（与 ord_orders.order_status / ord_meal_reservations.status 对齐）',
  `operator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID（用户或管理员）',
  `operator_type` TINYINT NOT NULL DEFAULT 1 COMMENT '操作人类型：1-用户, 2-商家/系统',
  `reason` VARCHAR(255) DEFAULT NULL COMMENT '变更原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`biz_type`, `order_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单状态历史表';

-- 4. 幂等记录表
CREATE TABLE `sys_idempotency` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '记录ID（雪花）',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键（如 WX_NOTIFY:{out_trade_no}）',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型（如 RECHARGE_NOTIFY / REFUND）',
  `biz_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '业务单ID',
  `request_hash` VARCHAR(64) DEFAULT NULL COMMENT '请求内容哈希（SHA-256，防重放）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-处理中, 1-成功',
  `response` TEXT DEFAULT NULL COMMENT '处理结果（JSON）',
  `expired_at` DATETIME DEFAULT NULL COMMENT '过期时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_expired_at` (`expired_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等记录表';
