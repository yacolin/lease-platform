-- ============================ 商品域 (prd_) ============================
-- prd_categories 商品分类 / prd_products 商品（SPU）/ prd_skus 商品SKU（1.3）/
-- prd_spec_groups 规格组（1.3）/ prd_spec_values 规格值（1.3）/
-- prd_daily_menus 每日菜单（正餐每日菜品）
-- 本文件可重复执行（先 DROP 再 CREATE）；删除或调整本域表时直接修改本文件
--
-- 表结构遵照 1.0 版本共享对话的 MySQL 设计（数据库 lease_db）；
-- 表名按 beauty_salon 约定加域前缀 `prd_`；跨域关系由服务层保证，本库暂不加外键约束。
-- 约定：业务表主键 BIGINT UNSIGNED（雪花，无自增；分类/规格组/规格值配置表自增，
-- 见 db/README.md 主键 ID 策略）；金额 BIGINT（分，最小单位整数，全链路统一以「分」为单位）；
-- 规格等结构化信息用 JSON 存储（spec_options / dish_details / spec_value_ids / spec_snapshot）。

-- 反向依赖顺序删除（prd_skus → prd_spec_values → prd_spec_groups →
-- prd_daily_menus → prd_products → prd_categories）
DROP TABLE IF EXISTS `prd_skus`;
DROP TABLE IF EXISTS `prd_spec_values`;
DROP TABLE IF EXISTS `prd_spec_groups`;
DROP TABLE IF EXISTS `prd_daily_menus`;
DROP TABLE IF EXISTS `prd_products`;
DROP TABLE IF EXISTS `prd_categories`;

-- 商品分类表：咖啡 / 正餐（含加餐、加汤）；1.3 支持二级分类（parent_id）/ 图标 / 展示状态
CREATE TABLE `prd_categories` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_name` VARCHAR(50) NOT NULL COMMENT '分类名称',
  `category_type` TINYINT NOT NULL COMMENT '类型：1-咖啡, 2-正餐',
  `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父分类ID（0=一级分类）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
  `icon_url` VARCHAR(255) DEFAULT NULL COMMENT '分类图标URL',
  `is_show` TINYINT NOT NULL DEFAULT 1 COMMENT '是否展示：0-不展示, 1-展示',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_type` (`category_type`),
  KEY `idx_parent_id` (`parent_id`),
  -- GET /public/categories：WHERE status=? AND is_show=? AND is_deleted=? ORDER BY sort_order,id
  -- （此前全表扫描 + filesort）
  KEY `idx_status_show_sort` (`status`, `is_show`, `is_deleted`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- 商品表（SPU，1.3 起收拢为 SPU，价格/库存下沉到 prd_skus）
-- 雪花 ID（业务主表，见 db/README.md 主键 ID 策略；种子显式指定 id）
CREATE TABLE `prd_products` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID（雪花）',
  `category_id` BIGINT UNSIGNED NOT NULL COMMENT '分类ID',
  `product_name` VARCHAR(100) NOT NULL COMMENT '商品名称',
  `product_type` TINYINT NOT NULL COMMENT '商品类型：1-咖啡, 2-正餐, 3-加饭/加菜, 4-加汤',
  `price` BIGINT NOT NULL COMMENT '原价（分，SPU 基准价；SKU 售价以 prd_skus.price 为准）',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '商品描述',
  `image_url` VARCHAR(255) DEFAULT NULL COMMENT '商品图片URL',
  `spec_options` JSON DEFAULT NULL COMMENT '规格选项JSON（1.0 遗留；1.3 由 prd_spec_groups/values 承接）',
  `is_available` TINYINT NOT NULL DEFAULT 1 COMMENT '是否可售：0-下架, 1-上架（与 product_status 联动）',
  `product_status` TINYINT NOT NULL DEFAULT 2 COMMENT '商品状态：0-草稿, 1-待审核, 2-上架, 3-下架, 4-停售',
  `stock` INT DEFAULT NULL COMMENT '库存（1.0 遗留；1.3 库存以 prd_skus.stock 为准）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_product_type` (`product_type`),
  -- GET /public/products 无筛选：WHERE is_available=1 AND product_status=2 AND is_deleted=0
  --   ORDER BY sort_order,id（实测：全表扫 5 万行 + filesort 57.4ms -> 索引 0.061ms）
  KEY `idx_avail_status_sort` (`is_available`, `product_status`, `is_deleted`, `sort_order`, `id`),
  -- GET /public/products?categoryId= ：按分类过滤后再按同一排序键分页
  KEY `idx_cat_avail_sort` (`category_id`, `is_available`, `product_status`, `is_deleted`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表（SPU）';

-- 商品规格组表（1.3）：同一商品的一组规格维度（如 杯型/温度/糖度）
CREATE TABLE `prd_spec_groups` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规格组ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID（SPU）',
  `group_name` VARCHAR(30) NOT NULL COMMENT '规格组名（如 杯型/温度/糖度）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_group` (`product_id`, `group_name`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品规格组表';

-- 商品规格值表（1.3）：规格组下的可选值（如 大杯/热/无糖）
CREATE TABLE `prd_spec_values` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '规格值ID',
  `group_id` BIGINT UNSIGNED NOT NULL COMMENT '规格组ID',
  `value_name` VARCHAR(30) NOT NULL COMMENT '规格值名（如 大杯/热/无糖）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_value` (`group_id`, `value_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品规格值表';

-- 商品 SKU 表（1.3）：SKU 承接价格/库存，规格组合快照
-- 雪花 ID（业务主表，见 db/README.md 主键 ID 策略；种子显式指定 id）
CREATE TABLE `prd_skus` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID（雪花）',
  `sku_code` VARCHAR(32) NOT NULL COMMENT 'SKU 编码',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID（SPU）',
  `spec_value_ids` JSON DEFAULT NULL COMMENT '规格值ID组合（如 [1,2,3]）',
  `spec_snapshot` JSON DEFAULT NULL COMMENT '规格快照（如 {"杯型":"大杯","温度":"热"}）',
  `price` BIGINT NOT NULL COMMENT '售价（分）',
  `cost_price` BIGINT DEFAULT NULL COMMENT '成本价（分）',
  `stock` INT DEFAULT NULL COMMENT '库存（NULL 表示不限）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-停售, 1-可售',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表';

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
  KEY `idx_product_id` (`product_id`),
  -- GET /public/menus?date= ：WHERE menu_date=? AND is_available=1
  --   ORDER BY product_id,dish_type,sort_order（原 idx_menu_date 命中后仍需 filesort）
  KEY `idx_date_avail_sort` (`menu_date`, `is_available`, `product_id`, `dish_type`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日菜单表';
