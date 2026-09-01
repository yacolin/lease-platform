-- ============================ 营销域 (mkt_) ============================
-- mkt_coupons 优惠券模板 / mkt_user_coupons 用户优惠券（1.6 运营/营销）
-- 本文件可重复执行（先 DROP 再 CREATE）；删除或调整本域表时直接修改本文件
--
-- 表名按 beauty_salon 约定加域前缀 `mkt_`（roadmap 2.0.5 领域边界：mkt_ 营销/优惠）；
-- 跨域关系由服务层保证，本库暂不加外键约束。
-- 约定：用户券主键 BIGINT UNSIGNED（雪花，无自增）；券模板配置表自增；
-- 金额 BIGINT（分，最小单位整数）；折扣率 DECIMAL(3,2)；JSON 存指定商品/分类。

-- 反向依赖顺序删除（mkt_user_coupons → mkt_coupons）
DROP TABLE IF EXISTS `mkt_user_coupons`;
DROP TABLE IF EXISTS `mkt_coupons`;

-- 优惠券模板表：满减 / 折扣，支持指定业务/商品/分类
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

-- 用户优惠券表：领取时快照券规则，使用绑定订单
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
