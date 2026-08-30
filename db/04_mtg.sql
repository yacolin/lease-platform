-- ============================ 会议室域 (mtg_) ============================
-- mtg_rooms 会议室 / mtg_room_level_prices 会议室等级定价 / mtg_reservations 会议室预约
-- 本文件可重复执行（先 DROP 再 CREATE）；删除或调整本域表时直接修改本文件
--
-- 表结构遵照 1.0 版本共享对话的 MySQL 设计（数据库 lease_db），1.1 按
-- 「预约规则/会员等级定价」共享对话补充定价模型：
--   * mtg_rooms 不再持有 hourly_fee（避免与 usr_member_levels.meeting_overtime_fee 冲突，
--     超出费用统一按「会员等级默认价 + 会议室覆盖价」解析）；
--   * 新增 mtg_room_level_prices：某会议室对某会员等级的超出费用覆盖（Override），
--     无覆盖时回落到 usr_member_levels.meeting_overtime_fee；
--   * mtg_reservations 新增 overtime_unit_price / free_hours_deducted 价格快照，
--     保证历史单按下单时单价对账（规则可改、快照不变）。
-- 表名按 beauty_salon 约定加域前缀 `mtg_`；跨域关系由服务层保证，本库暂不加外键约束。
-- 约定：业务表主键 BIGINT UNSIGNED（雪花，无自增，见 db/README.md 主键 ID 策略；
-- 会议室配置 mtg_rooms / 定价配置 mtg_room_level_prices 自增）；金额 BIGINT
-- （分，最小单位整数，全链路统一以「分」为单位）；时间字段统一 DATETIME
-- （预约日期 DATE + 时段 TIME）。

-- 反向依赖顺序删除（mtg_reservations → mtg_room_level_prices → mtg_rooms）
DROP TABLE IF EXISTS `mtg_reservations`;
DROP TABLE IF EXISTS `mtg_room_level_prices`;
DROP TABLE IF EXISTS `mtg_rooms`;

-- 会议室表：会议室基础信息（超出费用不在此配置，见 mtg_room_level_prices / usr_member_levels）
CREATE TABLE `mtg_rooms` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会议室ID',
  `room_name` VARCHAR(50) NOT NULL COMMENT '会议室名称',
  `capacity` INT NOT NULL COMMENT '容纳人数',
  `equipment` VARCHAR(255) DEFAULT NULL COMMENT '设备（投影仪、白板、音响等）',
  `suitable_scenes` VARCHAR(255) DEFAULT NULL COMMENT '适用场景（沙龙、培训、路演、商务洽谈）',
  `image_url` VARCHAR(255) DEFAULT NULL COMMENT '会议室图片',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-维护中, 1-可预约',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室表';

-- 会议室等级定价表：某会议室对某会员等级的超出费用覆盖（Override）
-- 取值逻辑：预约时优先查本表（room_id + level_code + is_active=1），
-- 有值用本表价格；无则回落 usr_member_levels.meeting_overtime_fee（非会员按 BASIC 兜底）。
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

-- 会议室预约表：会议室预约记录（含价格快照，历史单按快照对账）
CREATE TABLE `mtg_reservations` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '预约ID（雪花）',
  `reservation_no` VARCHAR(32) NOT NULL COMMENT '预约编号',
  `room_id` BIGINT UNSIGNED NOT NULL COMMENT '会议室ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '预约用户ID',
  `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
  `reservation_date` DATE NOT NULL COMMENT '预约日期',
  `start_time` TIME NOT NULL COMMENT '开始时间',
  `end_time` TIME NOT NULL COMMENT '结束时间',
  `duration_hours` DECIMAL(4,1) NOT NULL COMMENT '时长（小时）',
  `meeting_topic` VARCHAR(100) NOT NULL COMMENT '会议主题',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待确认, 1-已确认, 2-已完成, 3-已取消, 4-已过期',
  `is_free` TINYINT NOT NULL DEFAULT 0 COMMENT '是否免费：0-否（超出免费时长）, 1-是',
  `fee_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '费用（分）',
  `overtime_unit_price` BIGINT NOT NULL DEFAULT 0 COMMENT '价格快照：下单时超时单价（分/小时）',
  `free_hours_deducted` DECIMAL(4,1) NOT NULL DEFAULT 0 COMMENT '价格快照：本次抵扣的免费时长（小时）',
  `cancelled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
  `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_no` (`reservation_no`),
  KEY `idx_room_id` (`room_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_enterprise_id` (`enterprise_id`),
  KEY `idx_reservation_date` (`reservation_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室预约表';
