-- ============================================================================
-- 008_meeting_booking：1.4 会议室资源化（roadmap 1.4）
--   1. mtg_bookings 资源占用表（Booking）：Room → Resource → Booking 三层模型，
--      每次预约创建一条占用记录（占用中），取消/完成/过期释放；
--      时间冲突校验改查占用表（配合 mtg_rooms 行锁 SELECT ... FOR UPDATE 串行化
--      同会议室并发预约，保证 A 10:00-12:00 与 B 11:00-13:00 不能同时成功）；
--   2. mtg_reservations 新增 order_id：预约订单化（1.4.5）——会议室预约=业务事实，
--      关联 ord_orders（order_type=4 会议室）+ trd_payments（biz_type=5 会议室订单）=交易事实，
--      不再自维护一套支付逻辑（余额扣款/退款统一走 1.2 支付单/退款单）；
--   3. mtg_reservations.status 生命周期补「使用中」：0-待确认, 1-已确认, 2-使用中,
--      3-已完成, 4-已取消, 5-已过期（商家 已确认→使用中→已完成）；
--   4. ord_orders.order_type 补充 4-会议室；ord_order_status_history.biz_type 补充 3-会议室订单。
-- 与 db/04_mtg.sql、db/03_ord.sql 最终形态保持一致（双轨并行：全量重建 + 增量迁移）。
-- 约定：占用表主键雪花（BIGINT UNSIGNED 无自增）；时间统一 DATETIME。
-- ============================================================================

-- 1. 会议室资源占用表（Booking）
CREATE TABLE `mtg_bookings` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '占用记录ID（雪花）',
  `room_id` BIGINT UNSIGNED NOT NULL COMMENT '会议室ID',
  `reservation_id` BIGINT UNSIGNED NOT NULL COMMENT '预约ID（mtg_reservations.id）',
  `start_at` DATETIME NOT NULL COMMENT '占用开始时间',
  `end_at` DATETIME NOT NULL COMMENT '占用结束时间',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-占用中, 1-已释放',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_room_time` (`room_id`, `start_at`, `end_at`),
  KEY `idx_reservation_id` (`reservation_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室资源占用表（Booking）';

-- 2. 预约表：关联订单（预约订单化）+ 生命周期补「使用中」
ALTER TABLE `mtg_reservations`
  ADD COLUMN `order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联订单ID（1.4 预约订单化，付费预约才有）' AFTER `enterprise_id`,
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待确认, 1-已确认, 2-使用中, 3-已完成, 4-已取消, 5-已过期';

-- 3. 订单类型 / 状态历史业务类型补充会议室
ALTER TABLE `ord_orders`
  MODIFY COLUMN `order_type` TINYINT NOT NULL COMMENT '订单类型：1-咖啡, 2-正餐, 3-加餐, 4-会议室';
ALTER TABLE `ord_order_status_history`
  MODIFY COLUMN `biz_type` TINYINT NOT NULL DEFAULT 1 COMMENT '业务类型：1-咖啡订单, 2-正餐预订, 3-会议室订单';

-- +migrate Down
ALTER TABLE `ord_order_status_history`
  MODIFY COLUMN `biz_type` TINYINT NOT NULL DEFAULT 1 COMMENT '业务类型：1-咖啡订单, 2-正餐预订';
ALTER TABLE `ord_orders`
  MODIFY COLUMN `order_type` TINYINT NOT NULL COMMENT '订单类型：1-咖啡, 2-正餐, 3-加餐';
ALTER TABLE `mtg_reservations`
  DROP COLUMN `order_id`,
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待确认, 1-已确认, 2-已完成, 3-已取消, 4-已过期';
DROP TABLE IF EXISTS `mtg_bookings`;
