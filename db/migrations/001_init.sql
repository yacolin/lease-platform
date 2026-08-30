-- ============================================================================
-- 001_init：1.0 基线建表（19 张表 + 会员等级/充值档位/商品/每日菜单/会议室种子数据）
-- 内容与 db/[01-06]*.sql 的最终形态保持一致（reset_db.sh 与迁移双轨并行，
-- 改动任一表结构时必须同步两处）。
-- 约定：文件内不允许出现存储过程/函数体中的分号，执行器按行尾 ";" 切分执行。
-- ============================================================================

-- ---------- 用户域 ----------
CREATE TABLE `usr_users` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `openid` VARCHAR(64) NOT NULL COMMENT '微信OpenID',
  `unionid` VARCHAR(64) DEFAULT NULL COMMENT '微信UnionID',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `user_type` TINYINT NOT NULL DEFAULT 2 COMMENT '用户类型：1-超级管理员, 2-企业员工, 3-路人用户',
  `enterprise_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '所属企业ID（路人用户为空）',
  `member_level` TINYINT NOT NULL DEFAULT 0 COMMENT '会员等级：0-非会员, 1-基础版, 2-VIP版, 3-SVIP版',
  `is_enterprise_admin` TINYINT NOT NULL DEFAULT 0 COMMENT '是否企业管理员：0-否, 1-是',
  `balance` BIGINT NOT NULL DEFAULT 0 COMMENT '余额（分）',
  `gift_balance` BIGINT NOT NULL DEFAULT 0 COMMENT '赠送余额（分）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-正常',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_enterprise_id` (`enterprise_id`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `usr_enterprises` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '企业ID',
  `enterprise_name` VARCHAR(100) NOT NULL COMMENT '企业名称',
  `unified_social_credit_code` VARCHAR(50) NOT NULL COMMENT '统一社会信用代码',
  `business_license_url` VARCHAR(255) NOT NULL COMMENT '营业执照图片URL',
  `legal_person_name` VARCHAR(50) NOT NULL COMMENT '法人姓名',
  `legal_person_id_card_front` VARCHAR(255) NOT NULL COMMENT '法人身份证正面URL',
  `legal_person_id_card_back` VARCHAR(255) NOT NULL COMMENT '法人身份证反面URL',
  `contact_name` VARCHAR(50) NOT NULL COMMENT '联系人姓名',
  `contact_phone` VARCHAR(20) NOT NULL COMMENT '联系人手机号',
  `enterprise_type` TINYINT NOT NULL DEFAULT 0 COMMENT '企业类型：0-普通, 1-成长型, 2-高价值',
  `member_level` TINYINT NOT NULL DEFAULT 0 COMMENT '会员等级：0-非会员, 1-基础版, 2-VIP版, 3-SVIP版',
  `member_expire_at` DATETIME DEFAULT NULL COMMENT '会员到期时间',
  `audit_status` TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态：0-待审核, 1-审核通过, 2-审核拒绝',
  `audit_reason` VARCHAR(255) DEFAULT NULL COMMENT '审核拒绝原因',
  `audited_at` DATETIME DEFAULT NULL COMMENT '审核时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-正常',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credit_code` (`unified_social_credit_code`),
  KEY `idx_contact_phone` (`contact_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业表';

CREATE TABLE `usr_member_levels` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '等级ID',
  `level_code` VARCHAR(20) NOT NULL COMMENT '等级编码：BASIC/VIP/SVIP',
  `level_name` VARCHAR(50) NOT NULL COMMENT '等级名称',
  `price` BIGINT NOT NULL COMMENT '价格（分/年）',
  `discount_rate` DECIMAL(3,2) NOT NULL COMMENT '折扣率（如0.80表示8折）',
  `monthly_meeting_hours` INT NOT NULL DEFAULT 0 COMMENT '每月免费会议室时长（小时）',
  `meeting_booking_advance_days` INT NOT NULL DEFAULT 0 COMMENT '会议室提前预约天数',
  `meeting_priority` TINYINT NOT NULL DEFAULT 0 COMMENT '会议室预约优先级：0-无, 1-普通, 2-优先',
  `meeting_overtime_fee` BIGINT NOT NULL DEFAULT 0 COMMENT '会议室超出费用（分/小时）',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '权益描述',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_level_code` (`level_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员等级表';

CREATE TABLE `usr_enterprise_members` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `role` TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0-普通员工, 1-企业管理员',
  `invite_status` TINYINT NOT NULL DEFAULT 0 COMMENT '邀请状态：0-待接受, 1-已接受, 2-已拒绝',
  `invited_at` DATETIME DEFAULT NULL COMMENT '邀请时间',
  `accepted_at` DATETIME DEFAULT NULL COMMENT '接受时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enterprise_user` (`enterprise_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业员工表';

CREATE TABLE `usr_member_purchases` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '购买ID',
  `purchase_no` VARCHAR(32) NOT NULL COMMENT '购买编号',
  `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
  `member_level_id` BIGINT UNSIGNED NOT NULL COMMENT '会员等级ID',
  `original_price` BIGINT NOT NULL COMMENT '原价（分）',
  `pay_price` BIGINT NOT NULL COMMENT '实付价格（分）',
  `payment_method` TINYINT NOT NULL DEFAULT 1 COMMENT '支付方式：1-微信支付, 2-余额支付',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '微信支付交易号',
  `out_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '商户订单号',
  `start_date` DATE NOT NULL COMMENT '开始日期',
  `end_date` DATE NOT NULL COMMENT '结束日期（1年）',
  `payment_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态：0-待支付, 1-支付成功, 2-支付失败, 3-已退款',
  `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_no` (`purchase_no`),
  KEY `idx_enterprise_id` (`enterprise_id`),
  KEY `idx_payment_status` (`payment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员购买记录表';

-- ---------- 商品域 ----------
CREATE TABLE `prd_categories` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_name` VARCHAR(50) NOT NULL COMMENT '分类名称',
  `category_type` TINYINT NOT NULL COMMENT '类型：1-咖啡, 2-正餐',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_type` (`category_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

CREATE TABLE `prd_products` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `category_id` BIGINT UNSIGNED NOT NULL COMMENT '分类ID',
  `product_name` VARCHAR(100) NOT NULL COMMENT '商品名称',
  `product_type` TINYINT NOT NULL COMMENT '商品类型：1-咖啡, 2-正餐, 3-加饭/加菜, 4-加汤',
  `price` BIGINT NOT NULL COMMENT '原价（分）',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '商品描述',
  `image_url` VARCHAR(255) DEFAULT NULL COMMENT '商品图片URL',
  `spec_options` JSON DEFAULT NULL COMMENT '规格选项JSON（如杯型、温度、糖度）',
  `is_available` TINYINT NOT NULL DEFAULT 1 COMMENT '是否可售：0-下架, 1-上架',
  `stock` INT DEFAULT NULL COMMENT '库存（NULL表示不限）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_product_type` (`product_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

CREATE TABLE `prd_daily_menus` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_date` DATE NOT NULL COMMENT '菜单日期',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '关联套餐商品ID',
  `dish_name` VARCHAR(50) NOT NULL COMMENT '菜品名称',
  `dish_type` TINYINT NOT NULL COMMENT '菜品类型：1-荤菜, 2-素菜, 3-汤, 4-饭',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `is_available` TINYINT NOT NULL DEFAULT 1 COMMENT '是否供应：0-停售, 1-供应',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_menu_date` (`menu_date`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日菜单表';

-- ---------- 订单域 ----------
CREATE TABLE `ord_orders` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(32) NOT NULL COMMENT '订单编号',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '下单用户ID',
  `enterprise_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '企业ID（若为企业用户）',
  `order_type` TINYINT NOT NULL COMMENT '订单类型：1-咖啡, 2-正餐, 3-加餐',
  `order_status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付, 1-待取餐/配送, 2-制作中, 3-已完成, 4-已取消, 5-已退款',
  `total_amount` BIGINT NOT NULL COMMENT '商品原价总金额（分）',
  `discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '折扣优惠金额（分）',
  `member_discount` BIGINT NOT NULL DEFAULT 0 COMMENT '会员等级折扣金额（分）',
  `recharge_discount` BIGINT NOT NULL DEFAULT 0 COMMENT '充值赠送折扣金额（分）',
  `payable_amount` BIGINT NOT NULL COMMENT '应付金额（分，折后）',
  `payment_method` TINYINT DEFAULT NULL COMMENT '支付方式：1-余额支付, 2-微信支付',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '微信支付交易号',
  `out_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '商户订单号',
  `pickup_code` VARCHAR(10) DEFAULT NULL COMMENT '取餐码（咖啡）',
  `delivery_type` TINYINT DEFAULT NULL COMMENT '配送方式（正餐）：1-到店自取, 2-楼内配送, 3-周边配送',
  `delivery_fee` BIGINT DEFAULT 0 COMMENT '配送费（分）',
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

CREATE TABLE `ord_order_items` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `product_name` VARCHAR(100) NOT NULL COMMENT '商品名称（快照）',
  `product_price` BIGINT NOT NULL COMMENT '商品原价（分，快照）',
  `specification` JSON DEFAULT NULL COMMENT '规格选项JSON（如大杯/热/无糖）',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
  `subtotal` BIGINT NOT NULL COMMENT '小计（分，原价*数量）',
  `discounted_price` BIGINT NOT NULL COMMENT '折后单价（分）',
  `discounted_subtotal` BIGINT NOT NULL COMMENT '折后小计（分）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

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
  `delivery_fee` BIGINT NOT NULL DEFAULT 0 COMMENT '配送费（分）',
  `delivery_address` VARCHAR(255) DEFAULT NULL COMMENT '配送地址（周边配送）',
  `total_amount` BIGINT NOT NULL COMMENT '总金额（分）',
  `discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '折扣金额（分）',
  `payable_amount` BIGINT NOT NULL COMMENT '应付金额（分）',
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

CREATE TABLE `ord_meal_reservation_items` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `reservation_id` BIGINT UNSIGNED NOT NULL COMMENT '预订ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '套餐商品ID',
  `product_name` VARCHAR(100) NOT NULL COMMENT '套餐名称（快照）',
  `product_price` BIGINT NOT NULL COMMENT '套餐原价（分，快照）',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '份数',
  `subtotal` BIGINT NOT NULL COMMENT '小计（分）',
  `discounted_price` BIGINT NOT NULL COMMENT '折后单价（分）',
  `discounted_subtotal` BIGINT NOT NULL COMMENT '折后小计（分）',
  `dish_details` JSON DEFAULT NULL COMMENT '菜品明细JSON（当天菜单的快照）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_reservation_id` (`reservation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='正餐预订明细表';

-- ---------- 会议室域 ----------
CREATE TABLE `mtg_rooms` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会议室ID',
  `room_name` VARCHAR(50) NOT NULL COMMENT '会议室名称',
  `capacity` INT NOT NULL COMMENT '容纳人数',
  `equipment` VARCHAR(255) DEFAULT NULL COMMENT '设备（投影仪、白板、音响等）',
  `suitable_scenes` VARCHAR(255) DEFAULT NULL COMMENT '适用场景（沙龙、培训、路演、商务洽谈）',
  `image_url` VARCHAR(255) DEFAULT NULL COMMENT '会议室图片',
  `hourly_fee` BIGINT NOT NULL COMMENT '超出费用（分/小时）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-维护中, 1-可预约',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室表';

CREATE TABLE `mtg_reservations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预约ID',
  `reservation_no` VARCHAR(32) NOT NULL COMMENT '预约编号',
  `room_id` BIGINT UNSIGNED NOT NULL COMMENT '会议室ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '预约用户ID',
  `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
  `reservation_date` DATE NOT NULL COMMENT '预约日期',
  `start_time` TIME NOT NULL COMMENT '开始时间',
  `end_time` TIME NOT NULL COMMENT '结束时间',
  `duration_hours` DECIMAL(4,1) NOT NULL COMMENT '时长（小时）',
  `meeting_topic` VARCHAR(100) NOT NULL COMMENT '会议主题',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待确认, 1-已确认, 2-已完成, 3-已取消, 4-已过期',
  `is_free` TINYINT NOT NULL DEFAULT 0 COMMENT '是否免费：0-否（超出免费时长）, 1-是',
  `fee_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '费用（分）',
  `cancelled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
  `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_no` (`reservation_no`),
  KEY `idx_room_id` (`room_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_enterprise_id` (`enterprise_id`),
  KEY `idx_reservation_date` (`reservation_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室预约表';

-- ---------- 交易域 ----------
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

CREATE TABLE `trd_recharge_records` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `tier_id` BIGINT UNSIGNED NOT NULL COMMENT '充值档位ID',
  `recharge_amount` BIGINT NOT NULL COMMENT '充值金额（分）',
  `bonus_amount` BIGINT NOT NULL COMMENT '赠送金额（分）',
  `total_amount` BIGINT NOT NULL COMMENT '到账总金额（分）',
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

CREATE TABLE `trd_balance_transactions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `transaction_type` TINYINT NOT NULL COMMENT '类型：1-充值, 2-消费, 3-退款, 4-赠送, 5-调整',
  `amount` BIGINT NOT NULL COMMENT '变动金额（分，正数增加，负数减少）',
  `balance_before` BIGINT NOT NULL COMMENT '变动前余额（分）',
  `balance_after` BIGINT NOT NULL COMMENT '变动后余额（分）',
  `gift_balance_before` BIGINT NOT NULL DEFAULT 0 COMMENT '变动前赠送余额（分）',
  `gift_balance_after` BIGINT NOT NULL DEFAULT 0 COMMENT '变动后赠送余额（分）',
  `related_order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联订单ID（订单表）',
  `related_recharge_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联充值记录ID',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_related_order_id` (`related_order_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='余额流水表';

-- ---------- 系统域 ----------
CREATE TABLE `sys_notifications` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '接收用户ID',
  `notification_type` TINYINT NOT NULL COMMENT '通知类型：1-审核通过, 2-审核拒绝, 3-充值成功, 4-订单完成, 5-预约成功, 6-员工邀请, 7-其他',
  `title` VARCHAR(100) NOT NULL COMMENT '通知标题',
  `content` VARCHAR(500) NOT NULL COMMENT '通知内容',
  `template_id` VARCHAR(64) DEFAULT NULL COMMENT '微信模板ID',
  `send_status` TINYINT NOT NULL DEFAULT 0 COMMENT '发送状态：0-待发送, 1-发送成功, 2-发送失败',
  `send_time` DATETIME DEFAULT NULL COMMENT '发送时间',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读, 1-已读',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_send_status` (`send_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知记录表';

CREATE TABLE `sys_operation_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `operator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID（后台管理员）',
  `operator_type` TINYINT NOT NULL DEFAULT 1 COMMENT '操作人类型：1-系统管理员, 2-企业管理员',
  `operation_type` VARCHAR(50) NOT NULL COMMENT '操作类型（如审核企业、上架商品等）',
  `operation_content` TEXT COMMENT '操作内容',
  `ip_address` VARCHAR(45) DEFAULT NULL COMMENT '操作IP',
  `user_agent` VARCHAR(255) DEFAULT NULL COMMENT 'User-Agent',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ---------- 种子数据 ----------
INSERT INTO `usr_member_levels`
  (`level_code`, `level_name`, `price`, `discount_rate`, `monthly_meeting_hours`, `meeting_booking_advance_days`, `meeting_priority`, `meeting_overtime_fee`, `description`) VALUES
  ('BASIC', '基础版', 0, 0.95, 0, 0, 0, 8000, '老板本人9折/95折'),
  ('VIP',   'VIP版',   500000, 0.90, 4, 2, 1, 8000, '全公司员工8折/9折'),
  ('SVIP',  'SVIP版',  1200000, 0.85, 8, 1, 2, 8000, '全公司员工7折/85折');

INSERT INTO `trd_recharge_tiers` (`recharge_amount`, `bonus_amount`, `actual_amount`, `equivalent_discount`, `sort_order`) VALUES
  (20000, 2000, 22000, 0.91, 1),
  (50000, 6000, 56000, 0.89, 2),
  (100000, 15000, 115000, 0.87, 3),
  (200000, 40000, 240000, 0.83, 4);

INSERT INTO `prd_categories` (`category_name`, `category_type`, `sort_order`) VALUES
  ('咖啡',     1, 1),
  ('正餐',     2, 2),
  ('加餐/加菜', 2, 3),
  ('加汤',     2, 4);

-- id 显式指定，供 daily_menus 种子以 product_id 引用（4=3荤1素, 5=4荤1素, 8=加汤）
INSERT INTO `prd_products` (`id`, `category_id`, `product_name`, `product_type`, `price`, `description`, `spec_options`) VALUES
  (1, 1, '美式', 1, 1200, NULL, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
  (2, 1, '拿铁', 1, 1500, NULL, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
  (3, 1, '奶茶', 1, 1800, NULL, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
  (4, 2, '3荤1素套餐', 2, 2000, '每日更新菜单，3种荤菜+1种素菜', NULL),
  (5, 2, '4荤1素套餐', 2, 2500, '每日更新菜单，4种荤菜+1种素菜', NULL),
  (6, 3, '加饭', 3, 500, '额外加一份米饭', NULL),
  (7, 3, '加菜', 3, 500, '额外加一份菜品', NULL),
  (8, 4, '加汤', 4, 800, '额外加一份汤', NULL);

INSERT INTO `prd_daily_menus` (`menu_date`, `product_id`, `dish_name`, `dish_type`, `sort_order`) VALUES
  ('2026-08-30', 4, '红烧肉',   1, 1),
  ('2026-08-30', 4, '宫保鸡丁', 1, 2),
  ('2026-08-30', 4, '鱼香肉丝', 1, 3),
  ('2026-08-30', 4, '清炒时蔬', 2, 4),
  ('2026-08-30', 5, '红烧排骨',   1, 1),
  ('2026-08-30', 5, '辣子鸡',     1, 2),
  ('2026-08-30', 5, '水煮牛肉',   1, 3),
  ('2026-08-30', 5, '梅菜扣肉',   1, 4),
  ('2026-08-30', 5, '蒜蓉西兰花', 2, 5),
  ('2026-08-30', 8, '紫菜蛋花汤', 3, 1),
  ('2026-08-30', 8, '番茄蛋汤',   3, 2);

INSERT INTO `mtg_rooms` (`room_name`, `capacity`, `equipment`, `suitable_scenes`, `hourly_fee`) VALUES
  ('会议室A', 10, '投影仪、白板、音响', '沙龙、培训、路演、商务洽谈', 8000),
  ('会议室B', 10, '投影仪、白板、音响', '沙龙、培训、路演、商务洽谈', 8000),
  ('会议室C', 10, '投影仪、白板、音响', '沙龙、培训、路演、商务洽谈', 8000);

-- +migrate Down
-- 回滚：按反向依赖顺序删除（种子数据随表一并删除）
DROP TABLE IF EXISTS `sys_operation_logs`;
DROP TABLE IF EXISTS `sys_notifications`;
DROP TABLE IF EXISTS `ord_meal_reservation_items`;
DROP TABLE IF EXISTS `ord_meal_reservations`;
DROP TABLE IF EXISTS `ord_order_items`;
DROP TABLE IF EXISTS `ord_orders`;
DROP TABLE IF EXISTS `prd_daily_menus`;
DROP TABLE IF EXISTS `prd_products`;
DROP TABLE IF EXISTS `prd_categories`;
DROP TABLE IF EXISTS `mtg_reservations`;
DROP TABLE IF EXISTS `mtg_rooms`;
DROP TABLE IF EXISTS `trd_balance_transactions`;
DROP TABLE IF EXISTS `trd_recharge_records`;
DROP TABLE IF EXISTS `trd_recharge_tiers`;
DROP TABLE IF EXISTS `usr_member_purchases`;
DROP TABLE IF EXISTS `usr_enterprise_members`;
DROP TABLE IF EXISTS `usr_member_levels`;
DROP TABLE IF EXISTS `usr_enterprises`;
DROP TABLE IF EXISTS `usr_users`;
