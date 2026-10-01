-- V11__add_query_indexes：查询效率优化 —— 补齐热点查询缺失的索引
--
-- 背景：V1~V10 共 10 个迁移中没有任何一条 ADD INDEX，索引自建表后未再演进，
-- 而查询谓词已演进 10 个版本。EXPLAIN 实测 8 个 /api/v1/public/** 匿名只读接口
-- 全部走表扫描或 filesort；orders/stats 因 created_at 无索引而全表扫描。
--
-- 在 50k/200k/100k/300k 行数据集上实测（EXPLAIN ANALYZE）：
--   GET /public/products 无筛选  全表扫 5 万行 + filesort 57.4ms -> 索引 0.061ms（~940x）
--   GET /public/products 按分类  回表 12500 行 + filesort 15ms   -> 索引 0.11ms （~136x）
--   GET /orders/stats 今日       全表扫 20 万行 52ms             -> 索引 1.52ms （~34x）
--   GET /me/notifications 未读数 索引+事后过滤 0.13ms            -> 覆盖索引 0.0046ms（~28x）
--
-- 与 db/0*.sql（全量重建）和 db/migrations/011_add_query_indexes.sql 三处最终形态一致。
--
-- 注意：以下 3 条索引在 db/0*.sql（全量重建）轨道中已存在，但对应的 Flyway 迁移
-- 只加了列、漏建了索引 —— 两条建库轨道此前存在 schema 漂移
-- （见 docs/缓存与查询效率评估.md §2.1.3）。本迁移补上，使两轨对齐：
--   1. mtg_reservations.idx_order_id —— V8 加了 order_id 列未建索引
--   2. prd_categories.idx_parent_id  —— V7 加了 parent_id 列未建索引
--   3. ord_order_items.idx_sku_id    —— V7 加了 sku_id 列未建索引
--
-- Flyway 整文件执行（无 +migrate Down 回滚段）。

-- ── 用户域 usr ────────────────────────────────────────────────────────────

-- GET /enterprises：WHERE audit_status=? AND is_deleted=? ORDER BY id（COUNT + 分页双扫描）
ALTER TABLE `usr_enterprises` ADD INDEX `idx_audit_id` (`audit_status`, `is_deleted`, `id`);

-- GET /public/member-levels：WHERE status=? ORDER BY price（原全表扫描 + filesort）
ALTER TABLE `usr_member_levels` ADD INDEX `idx_status_price` (`status`, `price`, `id`);

-- 企业侧高频鉴权：WHERE user_id=? AND invite_status=? AND role=?（原 idx_user_id 无法覆盖后两列）
ALTER TABLE `usr_enterprise_members` ADD INDEX `idx_user_invite_role` (`user_id`, `invite_status`, `role`, `is_deleted`);

-- 成员列表：ORDER BY role DESC, accepted_at ASC
ALTER TABLE `usr_enterprise_members` ADD INDEX `idx_ent_role_accepted` (`enterprise_id`, `role`, `accepted_at`);

-- ── 商品域 prd ────────────────────────────────────────────────────────────

-- GET /public/categories：WHERE status=? AND is_show=? AND is_deleted=? ORDER BY sort_order,id
ALTER TABLE `prd_categories` ADD INDEX `idx_status_show_sort` (`status`, `is_show`, `is_deleted`, `sort_order`, `id`);

-- GET /public/products 无筛选：WHERE is_available=? AND product_status=? AND is_deleted=? ORDER BY sort_order,id
ALTER TABLE `prd_products` ADD INDEX `idx_avail_status_sort` (`is_available`, `product_status`, `is_deleted`, `sort_order`, `id`);

-- GET /public/products?categoryId= ：按分类过滤后按同一排序键分页
ALTER TABLE `prd_products` ADD INDEX `idx_cat_avail_sort` (`category_id`, `is_available`, `product_status`, `is_deleted`, `sort_order`, `id`);

-- GET /public/menus?date= ：WHERE menu_date=? AND is_available=? ORDER BY product_id,dish_type,sort_order
ALTER TABLE `prd_daily_menus` ADD INDEX `idx_date_avail_sort` (`menu_date`, `is_available`, `product_id`, `dish_type`, `sort_order`);

-- schema 漂移修复：V7 加了 parent_id 列但未建索引，db/02_prd.sql 有
-- 服务于 PrdCategoryService.delete 的「是否存在子分类」校验
ALTER TABLE `prd_categories` ADD INDEX `idx_parent_id` (`parent_id`);

-- ── 订单域 ord ────────────────────────────────────────────────────────────

-- GET /orders/stats 与 GET /orders 日期筛选：WHERE created_at 范围（此前 created_at 完全无索引）
ALTER TABLE `ord_orders` ADD INDEX `idx_created` (`created_at`, `id`);

-- GET /me/orders：WHERE user_id=? ORDER BY id DESC（原 idx_user_id 命中后仍 filesort）
ALTER TABLE `ord_orders` ADD INDEX `idx_user_id_id` (`user_id`, `id`);

-- GET /me/orders?status= ：user_id + order_status 过滤后仍按 id 排序
ALTER TABLE `ord_orders` ADD INDEX `idx_user_status_id` (`user_id`, `order_status`, `id`);

-- stats 各状态计数 + 管理端按状态分页
ALTER TABLE `ord_orders` ADD INDEX `idx_status_created` (`order_status`, `created_at`, `id`);

-- GET /me/meal-reservations：WHERE user_id=? [AND status=?] ORDER BY id DESC
ALTER TABLE `ord_meal_reservations` ADD INDEX `idx_user_id_id` (`user_id`, `id`);

-- GET /meal-reservations：WHERE menu_date=? [AND status=?] ORDER BY id DESC
ALTER TABLE `ord_meal_reservations` ADD INDEX `idx_date_status_id` (`menu_date`, `status`, `id`);

-- schema 漂移修复：V7 加了 sku_id 列但未建索引，db/03_ord.sql 有
-- 服务于 PrdSkuService.delete 的「SKU 是否被订单引用」校验
ALTER TABLE `ord_order_items` ADD INDEX `idx_sku_id` (`sku_id`);

-- ── 会议室域 mtg ──────────────────────────────────────────────────────────

-- GET /public/rooms 与 GET /rooms：WHERE status=? AND is_deleted=? ORDER BY id（本表原本只有主键）
ALTER TABLE `mtg_rooms` ADD INDEX `idx_status_deleted` (`status`, `is_deleted`, `id`);

-- 过期清理（由 GET 上的惰性清理改为 @Scheduled 分批）：WHERE status IN (0,1) AND reservation_date < ?
ALTER TABLE `mtg_reservations` ADD INDEX `idx_status_date` (`status`, `reservation_date`);

-- GET /me/meeting-reservations：WHERE user_id=? [AND status=?] ORDER BY id DESC
ALTER TABLE `mtg_reservations` ADD INDEX `idx_user_status_id` (`user_id`, `status`, `id`);

-- schema 漂移修复：V8 加了 order_id 列但未建索引，db/04_mtg.sql 有（见文件头说明）
ALTER TABLE `mtg_reservations` ADD INDEX `idx_order_id` (`order_id`);

-- 时段冲突检测：WHERE room_id=? AND status=0 AND start_at<? AND end_at>?（原 idx_room_time 未含 status）
ALTER TABLE `mtg_bookings` ADD INDEX `idx_room_status_time` (`room_id`, `status`, `start_at`, `end_at`);

-- ── 交易域 trd ────────────────────────────────────────────────────────────

-- GET /public/recharge-tiers 与管理端分页：WHERE status=? ORDER BY sort_order,id
ALTER TABLE `trd_recharge_tiers` ADD INDEX `idx_status_sort` (`status`, `sort_order`, `id`);

-- GET /payments 管理端：WHERE status=? [AND biz_type=?] ORDER BY id DESC
ALTER TABLE `trd_payments` ADD INDEX `idx_status_biz_id` (`status`, `biz_type`, `id`);

-- ── 系统域 sys ────────────────────────────────────────────────────────────

-- GET /me/notifications 与 /unread-count：WHERE user_id=? AND is_read=? ORDER BY id DESC（覆盖索引）
ALTER TABLE `sys_notifications` ADD INDEX `idx_user_read_id` (`user_id`, `is_read`, `id`);

-- ── 营销域 mkt ────────────────────────────────────────────────────────────

-- GET /public/coupons 与管理端分页：WHERE status=? ORDER BY sort_order,id
ALTER TABLE `mkt_coupons` ADD INDEX `idx_status_sort` (`status`, `sort_order`, `id`);

-- GET /me/coupons：expireUnused 按 expire_at 过滤 + 列表 ORDER BY id DESC
ALTER TABLE `mkt_user_coupons` ADD INDEX `idx_user_status_expire` (`user_id`, `status`, `expire_at`, `id`);
