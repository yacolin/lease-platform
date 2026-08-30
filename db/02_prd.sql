-- ============================ 商品域 (prd_) ============================
-- prd_categories 商品分类 / prd_products 商品（咖啡 / 正餐 / 加餐 / 加汤）/
-- prd_daily_menus 每日菜单（正餐每日菜品）
-- 本文件可重复执行（先 DROP 再 CREATE）；删除或调整本域表时直接修改本文件
--
-- 表结构遵照 1.0 版本共享对话的 MySQL 设计（数据库 lease_db）；
-- 表名按 beauty_salon 约定加域前缀 `prd_`；跨域关系由服务层保证，本库暂不加外键约束。
-- 约定：业务表主键 BIGINT UNSIGNED（雪花，无自增；分类配置 prd_categories 自增，
-- 见 db/README.md 主键 ID 策略）；金额 BIGINT（分，最小单位整数，全链路统一以「分」为单位）；
-- 规格等结构化信息用 JSON 存储（spec_options / dish_details）。

-- 反向依赖顺序删除（prd_daily_menus → prd_products → prd_categories）
DROP TABLE IF EXISTS `prd_daily_menus`;
DROP TABLE IF EXISTS `prd_products`;
DROP TABLE IF EXISTS `prd_categories`;

-- 商品分类表：咖啡 / 正餐（含加餐、加汤）
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

-- 商品表：咖啡和正餐的商品信息（规格用 JSON 存储）
-- 雪花 ID（业务主表，见 db/README.md 主键 ID 策略；种子显式指定 id）
CREATE TABLE `prd_products` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID（雪花）',
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

-- 每日菜单表：正餐每日菜单（每天的荤/素菜品明细）
-- 雪花 ID（按天自动生成、数据持续积累，见 db/README.md 主键 ID 策略；种子显式指定 id）
CREATE TABLE `prd_daily_menus` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '菜单ID（雪花）',
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
