-- ============================================================================
-- V9__acct_wallet：1.5 账户 / 钱包体系（roadmap 1.5）
--   1. acct_accounts 账户表：余额从 usr_users.balance / gift_balance 剥离，
--      账户 = 当前状态（可用余额 / 赠送余额 / 冻结余额），1:1 用户（账户 ID = 用户 ID）；
--   2. 存量余额回填：usr_users 现有 balance / gift_balance 迁入 acct_accounts 后删除列
--      （1.5 完成标准：User → Account → Balance → Ledger → Payment → Refund）；
--   3. trd_balance_transactions 流水表新增 frozen_balance_before / after：
--      冻结/解冻（押金/预授权/待结算）可审计（流水 = 历史事实）；
--   4. 余额消费策略（1.5.5）：赠送余额优先、余额混合扣款，规则落在 AccountService.debit。
-- 与 db/01_usr.sql、db/05_trd.sql、db/migrations/009_acct_wallet.sql、db/seed.py 最终形态一致
-- （Flyway 整文件执行，无 +migrate Down 回滚段）。
-- 约定：账户表主键 BIGINT UNSIGNED（= 用户 ID，1:1 无自增）；金额 BIGINT（分）。
-- ============================================================================

-- 1. 账户表（acct_ 前缀，归属交易域资金体系；roadmap 1.5.1）
CREATE TABLE `acct_accounts` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '账户ID（= 用户ID，1:1 账户无自增）',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `available_balance` BIGINT NOT NULL DEFAULT 0 COMMENT '可用余额（分）',
  `gift_balance` BIGINT NOT NULL DEFAULT 0 COMMENT '赠送余额（分）',
  `frozen_balance` BIGINT NOT NULL DEFAULT 0 COMMENT '冻结余额（分，押金/预授权/待结算/退款处理中）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-冻结, 1-正常',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户表';

-- 2. 存量余额回填（usr_users → acct_accounts）
INSERT INTO `acct_accounts` (`id`, `user_id`, `available_balance`, `gift_balance`, `frozen_balance`, `status`)
SELECT `id`, `id`, `balance`, `gift_balance`, 0, 1 FROM `usr_users`;

-- 3. 余额从用户基础资料剥离（roadmap 1.5 目标）
ALTER TABLE `usr_users`
  DROP COLUMN `balance`,
  DROP COLUMN `gift_balance`;

-- 4. 流水表补冻结余额前后值（冻结/解冻审计）
ALTER TABLE `trd_balance_transactions`
  ADD COLUMN `frozen_balance_before` BIGINT NOT NULL DEFAULT 0 COMMENT '变动前冻结余额（分）' AFTER `gift_balance_after`,
  ADD COLUMN `frozen_balance_after` BIGINT NOT NULL DEFAULT 0 COMMENT '变动后冻结余额（分）' AFTER `frozen_balance_before`;
