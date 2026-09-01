-- ============================================================================
-- V10__marketing：1.6 运营 / 营销能力（roadmap 1.6）
--   1. mkt_coupons 优惠券模板：满减 / 折扣，支持 指定业务（咖啡/正餐）/ 指定商品 / 指定分类；
--   2. mkt_user_coupons 用户券：领取时对券规则做快照（名称/类型/金额/折扣率/门槛/范围），
--      使用状态（0-未使用, 1-已使用, 2-已过期），绑定订单；
--   3. ord_orders 新增优惠券快照（coupon_id / coupon_name_snapshot / coupon_discount）：
--      优惠规则 → 订单优惠 → 优惠快照（roadmap 1.6.2，规则可改、订单快照不变）；
--   4. 折扣叠加链（1.6.4 Promotion 基础）：应付 = 原价 × 会员折扣率 × 充值折扣率 − 优惠券，
--      完整规则引擎（活动价/赠送等）留 2.0。
-- 与 db/07_mkt.sql、db/03_ord.sql、db/migrations/010_marketing.sql、db/seed.py 最终形态一致
-- （Flyway 整文件执行，无 +migrate Down 回滚段）。
-- 约定：用户券主键雪花（BIGINT UNSIGNED 无自增）；券模板配置表自增；
-- 金额 BIGINT（分）；折扣率 DECIMAL(3,2)；JSON 存指定商品/分类。
-- ============================================================================

-- 1. 优惠券模板表
CREATE TABLE `mkt_coupons` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '优惠券ID',
  `coupon_name` VARCHAR(50) NOT NULL COMMENT '优惠券名称',
  `coupon_type` TINYINT NOT NULL COMMENT '类型：1-满减, 2-折扣',
  `discount_amount` BIGINT DEFAULT NULL COMMENT '满减金额（分，coupon_type=1）',
  `discount_rate` DECIMAL(3,2) DEFAULT NULL COMMENT '折扣率（如 0.90 表示 9 折，coupon_type=2）',
  `threshold_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '使用门槛（分，满 X 可用；0=无门槛）',
  `biz_type` TINYINT DEFAULT NULL COMMENT '适用业务：1-咖啡, 2-正餐（NULL=全部）',
  `product_ids` JSON DEFAULT NULL COMMENT '指定商品ID（NULL=全部）',
  `category_ids` JSON DEFAULT NULL COMMENT '指定分类ID（NULL=全部）',
  `validity_days` INT NOT NULL DEFAULT 30 COMMENT '领取后有效天数',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-停用, 1-启用',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';

-- 2. 用户优惠券表（领取时快照券规则）
CREATE TABLE `mkt_user_coupons` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '用户券ID（雪花）',
  `coupon_id` BIGINT UNSIGNED NOT NULL COMMENT '优惠券模板ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `coupon_name` VARCHAR(50) NOT NULL COMMENT '券名称（快照）',
  `coupon_type` TINYINT NOT NULL COMMENT '类型：1-满减, 2-折扣（快照）',
  `discount_amount` BIGINT DEFAULT NULL COMMENT '满减金额（分，快照）',
  `discount_rate` DECIMAL(3,2) DEFAULT NULL COMMENT '折扣率（快照）',
  `threshold_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '使用门槛（分，快照）',
  `biz_type` TINYINT DEFAULT NULL COMMENT '适用业务（快照）',
  `product_ids` JSON DEFAULT NULL COMMENT '指定商品ID（快照）',
  `category_ids` JSON DEFAULT NULL COMMENT '指定分类ID（快照）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-未使用, 1-已使用, 2-已过期',
  `order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '使用订单ID',
  `used_at` DATETIME DEFAULT NULL COMMENT '使用时间',
  `expire_at` DATETIME NOT NULL COMMENT '过期时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_coupon_id` (`coupon_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券表';

-- 3. 订单优惠券快照（1.6.2：优惠规则 → 订单优惠 → 优惠快照）
ALTER TABLE `ord_orders`
  ADD COLUMN `coupon_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '优惠券ID（1.6）' AFTER `recharge_discount`,
  ADD COLUMN `coupon_name_snapshot` VARCHAR(50) DEFAULT NULL COMMENT '优惠券名称（快照）' AFTER `coupon_id`,
  ADD COLUMN `coupon_discount` BIGINT NOT NULL DEFAULT 0 COMMENT '优惠券抵扣金额（分）' AFTER `coupon_name_snapshot`;
