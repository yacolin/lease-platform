-- ============================================================================
-- V5__mtg_room_level_prices：会议室定价模型改造（1.1「预约规则/会员等级定价」共享对话）
--   1. mtg_rooms 删除 hourly_fee：超出费用不再由会议室持有（避免与
--      usr_member_levels.meeting_overtime_fee 冲突，防止 VIP/SVIP 同价或单会议室
--      单独调价时无法表达）；
--   2. 新增 mtg_room_level_prices：会议室 × 会员等级 的超出费用覆盖（Override），
--      无覆盖时回落等级默认价 usr_member_levels.meeting_overtime_fee；
--   3. mtg_reservations 新增价格快照 overtime_unit_price / free_hours_deducted：
--      历史单按下单时单价对账（规则可改、快照不变）；存量单默认 0（旧逻辑计费）。
-- 与 db/04_mtg.sql 最终形态保持一致（双轨并行：全量重建 + 增量迁移）。
-- ============================================================================

-- 1. 会议室等级定价表
CREATE TABLE `mtg_room_level_prices` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '定价ID',
  `room_id` BIGINT UNSIGNED NOT NULL COMMENT '会议室ID',
  `level_code` VARCHAR(20) NOT NULL COMMENT '会员等级编码：BASIC/VIP/SVIP',
  `overtime_fee` BIGINT NOT NULL COMMENT '该会议室针对该等级的超出费用（分/小时）',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用, 1-启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_level` (`room_id`, `level_code`),
  KEY `idx_level_code` (`level_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室等级定价表';

-- 2. 预约表价格快照
ALTER TABLE `mtg_reservations`
  ADD COLUMN `overtime_unit_price` BIGINT NOT NULL DEFAULT 0 COMMENT '价格快照：下单时超时单价（分/小时）' AFTER `fee_amount`,
  ADD COLUMN `free_hours_deducted` DECIMAL(4,1) NOT NULL DEFAULT 0 COMMENT '价格快照：本次抵扣的免费时长（小时）' AFTER `overtime_unit_price`;

-- 3. 会议室表删除超出费用字段
ALTER TABLE `mtg_rooms`
  DROP COLUMN `hourly_fee`;
