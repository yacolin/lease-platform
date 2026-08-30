-- ============================================================================
-- 004_recharge_paid_at：trd_recharge_records 补充支付时间列（1.0 设计遗漏，
-- 实体/充值入账逻辑已使用 paid_at；与 db/05_trd.sql 最终形态保持一致，双轨并行）
-- ============================================================================

ALTER TABLE `trd_recharge_records`
  ADD COLUMN `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间' AFTER `payment_status`;

-- +migrate Down
ALTER TABLE `trd_recharge_records` DROP COLUMN `paid_at`;
