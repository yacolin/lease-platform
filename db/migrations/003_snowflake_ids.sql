-- ============================================================================
-- 003_snowflake_ids：业务表主键改雪花（去掉 AUTO_INCREMENT）
-- 依据 db/README.md「主键 ID 策略」：14 张核心/资金/流水表由 MyBatis-Plus 雪花
-- 生成 ID（IdType.ASSIGN_ID），DDL 去掉 AUTO_INCREMENT，避免 DB 误生成小 ID
-- 混入雪花空间；6 张配置/枚举/账号表（usr_member_levels / usr_admins /
-- prd_categories / mtg_rooms / trd_recharge_tiers / sys_notifications）保持自增。
-- 与 db/0*.sql 最终形态保持一致（双轨并行）。
-- ============================================================================

ALTER TABLE `usr_users`                MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID（雪花）';
ALTER TABLE `usr_enterprises`          MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID（雪花）';
ALTER TABLE `usr_enterprise_members`   MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '记录ID（雪花）';
ALTER TABLE `usr_member_purchases`     MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '购买ID（雪花）';
ALTER TABLE `prd_products`             MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID（雪花）';
ALTER TABLE `prd_daily_menus`          MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '菜单ID（雪花）';
ALTER TABLE `ord_orders`               MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '订单ID（雪花）';
ALTER TABLE `ord_order_items`          MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '明细ID（雪花）';
ALTER TABLE `ord_meal_reservations`    MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '预订ID（雪花）';
ALTER TABLE `ord_meal_reservation_items` MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '明细ID（雪花）';
ALTER TABLE `mtg_reservations`         MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '预约ID（雪花）';
ALTER TABLE `trd_recharge_records`     MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '记录ID（雪花）';
ALTER TABLE `trd_balance_transactions` MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '流水ID（雪花）';
ALTER TABLE `sys_operation_logs`       MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL COMMENT '日志ID（雪花）';

-- +migrate Down
-- 回滚：恢复自增（仅当确定无雪花数据混入时执行；已有雪花行时自增会与雪花空间冲突）
ALTER TABLE `usr_users`                MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID';
ALTER TABLE `usr_enterprises`          MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '企业ID';
ALTER TABLE `usr_enterprise_members`   MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID';
ALTER TABLE `usr_member_purchases`     MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '购买ID';
ALTER TABLE `prd_products`             MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品ID';
ALTER TABLE `prd_daily_menus`          MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜单ID';
ALTER TABLE `ord_orders`               MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID';
ALTER TABLE `ord_order_items`          MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID';
ALTER TABLE `ord_meal_reservations`    MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预订ID';
ALTER TABLE `ord_meal_reservation_items` MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID';
ALTER TABLE `mtg_reservations`         MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预约ID';
ALTER TABLE `trd_recharge_records`     MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID';
ALTER TABLE `trd_balance_transactions` MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水ID';
ALTER TABLE `sys_operation_logs`       MODIFY COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID';
