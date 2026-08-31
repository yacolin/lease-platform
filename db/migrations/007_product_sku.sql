-- ============================================================================
-- 007_product_sku：1.3 商品中心 SKU 化（roadmap 1.3）
--   1. prd_spec_groups / prd_spec_values：商品规格组与规格值（如 杯型/温度/糖度）；
--   2. prd_skus：SKU（sku_code / product_id / 规格组合 / price / cost_price / stock /
--      status），prd_products 收拢为 SPU，价格与库存下沉到 SKU；
--   3. prd_products 新增 product_status 商品状态生命周期（0-草稿, 1-待审核, 2-上架,
--      3-下架, 4-停售），替代简单 is_available 0/1；
--   4. prd_categories 分类增强（parent_id 二级分类 / icon_url 图标 / is_show 展示状态）；
--   5. ord_order_items 订单明细新增 SKU 快照（sku_id / sku_name_snapshot /
--      sku_price_snapshot / specification_snapshot），历史字段保留（快照原则）。
-- 与 db/02_prd.sql、db/03_ord.sql、db/seed.py 最终形态保持一致
-- （双轨并行：全量重建 + 增量迁移）。
-- 约定：SKU 为主键雪花（BIGINT UNSIGNED 无自增）；规格组/规格值为配置表自增；
-- 金额 BIGINT（分）；sku_code 唯一。
-- ============================================================================

-- 1. 商品规格组表
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

-- 2. 商品规格值表
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

-- 3. 商品 SKU 表
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

-- 4. 商品状态生命周期（is_available 保留兼容，与 product_status 联动）
ALTER TABLE `prd_products`
  ADD COLUMN `product_status` TINYINT NOT NULL DEFAULT 2 COMMENT '商品状态：0-草稿, 1-待审核, 2-上架, 3-下架, 4-停售' AFTER `is_available`;

-- 5. 商品分类增强（二级分类 / 图标 / 展示状态）
ALTER TABLE `prd_categories`
  ADD COLUMN `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父分类ID（0=一级分类）' AFTER `category_type`,
  ADD COLUMN `icon_url` VARCHAR(255) DEFAULT NULL COMMENT '分类图标URL' AFTER `status`,
  ADD COLUMN `is_show` TINYINT NOT NULL DEFAULT 1 COMMENT '是否展示：0-不展示, 1-展示' AFTER `status`;

-- 6. 订单明细 SKU 快照（历史 product_id/product_price/specification 保留）
ALTER TABLE `ord_order_items`
  ADD COLUMN `sku_id` BIGINT UNSIGNED DEFAULT NULL COMMENT 'SKU ID' AFTER `product_id`,
  ADD COLUMN `sku_name_snapshot` VARCHAR(100) DEFAULT NULL COMMENT 'SKU 名称快照（编码）' AFTER `sku_id`,
  ADD COLUMN `sku_price_snapshot` BIGINT DEFAULT NULL COMMENT 'SKU 单价快照（分）' AFTER `sku_name_snapshot`,
  ADD COLUMN `specification_snapshot` JSON DEFAULT NULL COMMENT '规格快照（SKU 解析后）' AFTER `sku_price_snapshot`;

-- +migrate Down
ALTER TABLE `ord_order_items`
  DROP COLUMN `specification_snapshot`,
  DROP COLUMN `sku_price_snapshot`,
  DROP COLUMN `sku_name_snapshot`,
  DROP COLUMN `sku_id`;
ALTER TABLE `prd_categories`
  DROP COLUMN `is_show`,
  DROP COLUMN `icon_url`,
  DROP COLUMN `parent_id`;
ALTER TABLE `prd_products`
  DROP COLUMN `product_status`;
DROP TABLE IF EXISTS `prd_skus`;
DROP TABLE IF EXISTS `prd_spec_values`;
DROP TABLE IF EXISTS `prd_spec_groups`;
