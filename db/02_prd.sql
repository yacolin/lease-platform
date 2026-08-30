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

-- ---------- 种子数据 ----------
-- 1. 商品分类（咖啡 / 正餐 / 加餐加菜 / 加汤）
INSERT INTO `prd_categories` (`category_name`, `category_type`, `sort_order`) VALUES
  ('咖啡',     1, 1),
  ('正餐',     2, 2),
  ('加餐/加菜', 2, 3),
  ('加汤',     2, 4);

-- 2. 商品（id 显式指定，供 prd_daily_menus 种子以 product_id 引用；
--    咖啡清单按共享对话截断前的商品补齐，后续可继续追加）
INSERT INTO `prd_products` (`id`, `category_id`, `product_name`, `product_type`, `price`, `description`, `spec_options`) VALUES
  (1, 1, '美式', 1, 1200, NULL, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
  (2, 1, '拿铁', 1, 1500, NULL, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
  (3, 1, '奶茶', 1, 1800, NULL, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
  (4, 2, '3荤1素套餐', 2, 2000, '每日更新菜单，3种荤菜+1种素菜', NULL),
  (5, 2, '4荤1素套餐', 2, 2500, '每日更新菜单，4种荤菜+1种素菜', NULL),
  (6, 3, '加饭', 3, 500, '额外加一份米饭', NULL),
  (7, 3, '加菜', 3, 500, '额外加一份菜品', NULL),
  (8, 4, '加汤', 4, 800, '额外加一份汤', NULL);

-- 3. 每日菜单示例（2026-08-30；product_id 对应上方套餐/加汤商品；id 显式指定，雪花表无自增）
INSERT INTO `prd_daily_menus` (`id`, `menu_date`, `product_id`, `dish_name`, `dish_type`, `sort_order`) VALUES
  -- 3荤1素套餐（product_id=4）的菜品
  (1,  '2026-08-30', 4, '红烧肉',   1, 1),
  (2,  '2026-08-30', 4, '宫保鸡丁', 1, 2),
  (3,  '2026-08-30', 4, '鱼香肉丝', 1, 3),
  (4,  '2026-08-30', 4, '清炒时蔬', 2, 4),
  -- 4荤1素套餐（product_id=5）的菜品
  (5,  '2026-08-30', 5, '红烧排骨',   1, 1),
  (6,  '2026-08-30', 5, '辣子鸡',     1, 2),
  (7,  '2026-08-30', 5, '水煮牛肉',   1, 3),
  (8,  '2026-08-30', 5, '梅菜扣肉',   1, 4),
  (9,  '2026-08-30', 5, '蒜蓉西兰花', 2, 5),
  -- 加汤（product_id=8）
  (10, '2026-08-30', 8, '紫菜蛋花汤', 3, 1),
  (11, '2026-08-30', 8, '番茄蛋汤',   3, 2);
