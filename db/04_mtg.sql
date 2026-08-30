-- ============================ 会议室域 (mtg_) ============================
-- mtg_rooms 会议室 / mtg_reservations 会议室预约
-- 本文件可重复执行（先 DROP 再 CREATE）；删除或调整本域表时直接修改本文件
--
-- 表结构遵照 1.0 版本共享对话的 MySQL 设计（数据库 lease_db）；
-- 表名按 beauty_salon 约定加域前缀 `mtg_`；跨域关系由服务层保证，本库暂不加外键约束。
-- 约定：业务表主键 BIGINT UNSIGNED（雪花，无自增，见 db/README.md 主键 ID 策略；
-- 会议室配置 mtg_rooms 自增）；金额 BIGINT（分，最小单位整数，全链路统一以「分」为单位）；
-- 时间字段统一 DATETIME（预约日期 DATE + 时段 TIME）。

-- 反向依赖顺序删除（mtg_reservations → mtg_rooms）
DROP TABLE IF EXISTS `mtg_reservations`;
DROP TABLE IF EXISTS `mtg_rooms`;

-- 会议室表：会议室基础信息
CREATE TABLE `mtg_rooms` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会议室ID',
  `room_name` VARCHAR(50) NOT NULL COMMENT '会议室名称',
  `capacity` INT NOT NULL COMMENT '容纳人数',
  `equipment` VARCHAR(255) DEFAULT NULL COMMENT '设备（投影仪、白板、音响等）',
  `suitable_scenes` VARCHAR(255) DEFAULT NULL COMMENT '适用场景（沙龙、培训、路演、商务洽谈）',
  `image_url` VARCHAR(255) DEFAULT NULL COMMENT '会议室图片',
  `hourly_fee` BIGINT NOT NULL COMMENT '超出费用（分/小时）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-维护中, 1-可预约',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室表';

-- 会议室预约表：会议室预约记录
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

-- ---------- 种子数据（会议室） ----------
INSERT INTO `mtg_rooms` (`room_name`, `capacity`, `equipment`, `suitable_scenes`, `hourly_fee`) VALUES
  ('会议室A', 10, '投影仪、白板、音响', '沙龙、培训、路演、商务洽谈', 8000),
  ('会议室B', 10, '投影仪、白板、音响', '沙龙、培训、路演、商务洽谈', 8000),
  ('会议室C', 10, '投影仪、白板、音响', '沙龙、培训、路演、商务洽谈', 8000);
