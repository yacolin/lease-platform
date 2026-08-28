-- ============================ 订单域 (ord_) ============================
-- ord_orders 订单主表（咖啡点单 / 正餐预订） / ord_order_items 订单明细 /
-- ord_meal_reservations 正餐预订 / ord_meal_reservation_items 正餐预订明细
-- 本文件可重复执行（先 DROP 再 CREATE）；删除或调整本域表时直接修改本文件
--
-- 表结构遵照 1.0 版本共享对话的 MySQL 设计（数据库 lease_db）；
-- 表名按 beauty_salon 约定加域前缀 `ord_`；跨域关系由服务层保证，本库暂不加外键约束。
-- 约定：主键 BIGINT UNSIGNED AUTO_INCREMENT；金额 DECIMAL(10,2)（元）；
-- 业务编号（order_no / reservation_no / purchase_no）建唯一索引。

-- 反向依赖顺序删除（ord_meal_reservation_items → ord_meal_reservations → ord_order_items → ord_orders）
DROP TABLE IF EXISTS `ord_meal_reservation_items`;
DROP TABLE IF EXISTS `ord_meal_reservations`;
DROP TABLE IF EXISTS `ord_order_items`;
DROP TABLE IF EXISTS `ord_orders`;

-- 订单主表：存储所有类型的订单（咖啡点单、正餐预订）
CREATE TABLE `ord_orders` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(32) NOT NULL COMMENT '订单编号',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '下单用户ID',
  `enterprise_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '企业ID（若为企业用户）',
  `order_type` TINYINT NOT NULL COMMENT '订单类型：1-咖啡, 2-正餐, 3-加餐',
  `order_status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付, 1-待取餐/配送, 2-制作中, 3-已完成, 4-已取消, 5-已退款',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '商品原价总金额',
  `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '折扣优惠金额',
  `member_discount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '会员等级折扣金额',
  `recharge_discount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '充值赠送折扣金额',
  `payable_amount` DECIMAL(10,2) NOT NULL COMMENT '应付金额（折后）',
  `payment_method` TINYINT DEFAULT NULL COMMENT '支付方式：1-余额支付, 2-微信支付',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '微信支付交易号',
  `out_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '商户订单号',
  `pickup_code` VARCHAR(10) DEFAULT NULL COMMENT '取餐码（咖啡）',
  `delivery_type` TINYINT DEFAULT NULL COMMENT '配送方式（正餐）：1-到店自取, 2-楼内配送, 3-周边配送',
  `delivery_fee` DECIMAL(10,2) DEFAULT 0.00 COMMENT '配送费',
  `delivery_address` VARCHAR(255) DEFAULT NULL COMMENT '配送地址（周边配送）',
  `reservation_date` DATE DEFAULT NULL COMMENT '预约日期（正餐）',
  `reservation_time` TIME DEFAULT NULL COMMENT '预约时段（正餐）',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '订单备注',
  `estimated_delivery_time` DATETIME DEFAULT NULL COMMENT '预计送达时间',
  `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间',
  `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `cancelled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
  `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_pickup_code` (`pickup_code`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_type` (`order_type`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_reservation_date` (`reservation_date`),
  KEY `idx_out_trade_no` (`out_trade_no`),
  KEY `idx_enterprise_id` (`enterprise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 订单明细表：订单中每个商品的详细信息（含规格快照）
CREATE TABLE `ord_order_items` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `product_name` VARCHAR(100) NOT NULL COMMENT '商品名称（快照）',
  `product_price` DECIMAL(10,2) NOT NULL COMMENT '商品原价（快照）',
  `specification` JSON DEFAULT NULL COMMENT '规格选项JSON（如大杯/热/无糖）',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
  `subtotal` DECIMAL(10,2) NOT NULL COMMENT '小计（原价*数量）',
  `discounted_price` DECIMAL(10,2) NOT NULL COMMENT '折后单价',
  `discounted_subtotal` DECIMAL(10,2) NOT NULL COMMENT '折后小计',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

-- 正餐预订表：正餐预订与普通咖啡点单分开管理
CREATE TABLE `ord_meal_reservations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预订ID',
  `reservation_no` VARCHAR(32) NOT NULL COMMENT '预订编号',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '预订用户ID',
  `enterprise_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '企业ID（若为企业用户）',
  `order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联订单ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '套餐商品ID',
  `product_name` VARCHAR(100) NOT NULL COMMENT '套餐名称（快照）',
  `menu_date` DATE NOT NULL COMMENT '菜单日期（预订的日期）',
  `reservation_date` DATE NOT NULL COMMENT '预订日期',
  `reservation_time_slot` VARCHAR(20) NOT NULL COMMENT '预订时段：午餐/晚餐',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '份数',
  `delivery_type` TINYINT NOT NULL DEFAULT 1 COMMENT '配送方式：1-到店自取, 2-楼内配送, 3-周边配送',
  `delivery_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '配送费',
  `delivery_address` VARCHAR(255) DEFAULT NULL COMMENT '配送地址（周边配送）',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '总金额',
  `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '折扣金额',
  `payable_amount` DECIMAL(10,2) NOT NULL COMMENT '应付金额',
  `payment_method` TINYINT DEFAULT NULL COMMENT '支付方式：1-余额支付, 2-微信支付',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '微信支付交易号',
  `out_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '商户订单号',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待支付, 1-已支付/待备餐, 2-备餐中, 3-已完成, 4-已取消, 5-已退款',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间',
  `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `cancelled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
  `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_no` (`reservation_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_enterprise_id` (`enterprise_id`),
  KEY `idx_menu_date` (`menu_date`),
  KEY `idx_status` (`status`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='正餐预订表';

-- 正餐预订明细表：正餐预订中每个套餐的详细信息
CREATE TABLE `ord_meal_reservation_items` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `reservation_id` BIGINT UNSIGNED NOT NULL COMMENT '预订ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '套餐商品ID',
  `product_name` VARCHAR(100) NOT NULL COMMENT '套餐名称（快照）',
  `product_price` DECIMAL(10,2) NOT NULL COMMENT '套餐原价（快照）',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '份数',
  `subtotal` DECIMAL(10,2) NOT NULL COMMENT '小计',
  `discounted_price` DECIMAL(10,2) NOT NULL COMMENT '折后单价',
  `discounted_subtotal` DECIMAL(10,2) NOT NULL COMMENT '折后小计',
  `dish_details` JSON DEFAULT NULL COMMENT '菜品明细JSON（当天菜单的快照）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_reservation_id` (`reservation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='正餐预订明细表';
