-- ============================ 系统域 (sys_) ============================
-- sys_notifications 通知记录 / sys_operation_logs 操作日志 /
-- sys_idempotency 幂等记录（1.2）
-- 本文件可重复执行（先 DROP 再 CREATE）；删除或调整本域表时直接修改本文件
--
-- 表结构遵照 1.0 版本共享对话的 MySQL 设计（数据库 lease_db）；
-- 表名按 beauty_salon 约定加域前缀 `sys_`；跨域关系由服务层保证，本库暂不加外键约束。
-- 约定：业务表主键 BIGINT UNSIGNED（雪花，无自增，见 db/README.md 主键 ID 策略；
-- 通知记录 sys_notifications 自增）；纯流水表只保留 created_at；
-- 幂等记录（1.2）：idempotency_key 唯一（防外部重复回调）。

-- 反向依赖顺序删除（sys_idempotency → sys_notifications → sys_operation_logs）
DROP TABLE IF EXISTS `sys_idempotency`;
DROP TABLE IF EXISTS `sys_notifications`;
DROP TABLE IF EXISTS `sys_operation_logs`;

-- 通知记录表：微信模板消息发送记录
CREATE TABLE `sys_notifications` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '接收用户ID',
  `notification_type` TINYINT NOT NULL COMMENT '通知类型：1-审核通过, 2-审核拒绝, 3-充值成功, 4-订单完成, 5-预约成功, 6-员工邀请, 7-其他',
  `title` VARCHAR(100) NOT NULL COMMENT '通知标题',
  `content` VARCHAR(500) NOT NULL COMMENT '通知内容',
  `template_id` VARCHAR(64) DEFAULT NULL COMMENT '微信模板ID',
  `send_status` TINYINT NOT NULL DEFAULT 0 COMMENT '发送状态：0-待发送, 1-发送成功, 2-发送失败',
  `send_time` DATETIME DEFAULT NULL COMMENT '发送时间',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读, 1-已读',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_send_status` (`send_status`),
  -- GET /me/notifications 与 /unread-count：WHERE user_id=? AND is_read=? ORDER BY id DESC
  -- （原 idx_user_id 命中后 is_read 只能事后过滤；实测改为覆盖索引后 0.13ms -> 0.0046ms）
  KEY `idx_user_read_id` (`user_id`, `is_read`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知记录表';

-- 操作日志表：管理员和后台的操作行为
CREATE TABLE `sys_operation_logs` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '日志ID（雪花）',
  `operator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID（后台管理员）',
  `operator_type` TINYINT NOT NULL DEFAULT 1 COMMENT '操作人类型：1-系统管理员, 2-企业管理员',
  `operation_type` VARCHAR(50) NOT NULL COMMENT '操作类型（如审核企业、上架商品等）',
  `operation_content` TEXT COMMENT '操作内容',
  `ip_address` VARCHAR(45) DEFAULT NULL COMMENT '操作IP',
  `user_agent` VARCHAR(255) DEFAULT NULL COMMENT 'User-Agent',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 幂等记录表（1.2 交易可靠性）：外部重复回调/请求去重（微信回调、充值回调、退款回调等）
CREATE TABLE `sys_idempotency` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '记录ID（雪花）',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键（如 WX_NOTIFY:{out_trade_no}）',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型（如 RECHARGE_NOTIFY / REFUND）',
  `biz_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '业务单ID',
  `request_hash` VARCHAR(64) DEFAULT NULL COMMENT '请求内容哈希（SHA-256，防重放）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-处理中, 1-成功',
  `response` TEXT DEFAULT NULL COMMENT '处理结果（JSON）',
  `expired_at` DATETIME DEFAULT NULL COMMENT '过期时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_expired_at` (`expired_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等记录表';
