-- ============================ 用户域 (usr_) ============================
-- usr_users 微信用户 / usr_enterprises 企业 / usr_member_levels 会员等级 /
-- usr_enterprise_members 企业员工 / usr_member_purchases 会员购买记录
-- 本文件可重复执行（先 DROP 再 CREATE）；删除或调整本域表时直接修改本文件
--
-- 表结构遵照 1.0 版本共享对话的 MySQL 设计（数据库 lease_db）；
-- 表名按 beauty_salon 约定加域前缀 `usr_`；跨域关系由服务层保证，本库暂不加外键约束。
-- 约定：业务表主键 BIGINT UNSIGNED（雪花，无自增；配置/账号类表如 usr_member_levels /
-- usr_admins 自增，见 db/README.md 主键 ID 策略）；金额 DECIMAL(10,2)（元）；
-- 逻辑删除 is_deleted（默认 0）；创建/更新时间 created_at / updated_at。

-- 反向依赖顺序删除（usr_member_purchases → usr_enterprise_members → usr_member_levels / usr_enterprises → usr_users → usr_admins）
DROP TABLE IF EXISTS `usr_member_purchases`;
DROP TABLE IF EXISTS `usr_enterprise_members`;
DROP TABLE IF EXISTS `usr_member_levels`;
DROP TABLE IF EXISTS `usr_enterprises`;
DROP TABLE IF EXISTS `usr_users`;
DROP TABLE IF EXISTS `usr_admins`;

-- 用户表：所有用户（超级管理员 / 企业员工 / 路人用户）
CREATE TABLE `usr_users` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID（雪花）',
  `openid` VARCHAR(64) NOT NULL COMMENT '微信OpenID',
  `unionid` VARCHAR(64) DEFAULT NULL COMMENT '微信UnionID',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `user_type` TINYINT NOT NULL DEFAULT 2 COMMENT '用户类型：1-超级管理员, 2-企业员工, 3-路人用户',
  `enterprise_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '所属企业ID（路人用户为空）',
  `member_level` TINYINT NOT NULL DEFAULT 0 COMMENT '会员等级：0-非会员, 1-基础版, 2-VIP版, 3-SVIP版',
  `is_enterprise_admin` TINYINT NOT NULL DEFAULT 0 COMMENT '是否企业管理员：0-否, 1-是',
  `balance` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '余额（充值金额）',
  `gift_balance` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '赠送余额',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-正常',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_enterprise_id` (`enterprise_id`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 企业表：企业实名认证信息
CREATE TABLE `usr_enterprises` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID（雪花）',
  `enterprise_name` VARCHAR(100) NOT NULL COMMENT '企业名称',
  `unified_social_credit_code` VARCHAR(50) NOT NULL COMMENT '统一社会信用代码',
  `business_license_url` VARCHAR(255) NOT NULL COMMENT '营业执照图片URL',
  `legal_person_name` VARCHAR(50) NOT NULL COMMENT '法人姓名',
  `legal_person_id_card_front` VARCHAR(255) NOT NULL COMMENT '法人身份证正面URL',
  `legal_person_id_card_back` VARCHAR(255) NOT NULL COMMENT '法人身份证反面URL',
  `contact_name` VARCHAR(50) NOT NULL COMMENT '联系人姓名',
  `contact_phone` VARCHAR(20) NOT NULL COMMENT '联系人手机号',
  `enterprise_type` TINYINT NOT NULL DEFAULT 0 COMMENT '企业类型：0-普通, 1-成长型, 2-高价值',
  `member_level` TINYINT NOT NULL DEFAULT 0 COMMENT '会员等级：0-非会员, 1-基础版, 2-VIP版, 3-SVIP版',
  `member_expire_at` DATETIME DEFAULT NULL COMMENT '会员到期时间',
  `audit_status` TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态：0-待审核, 1-审核通过, 2-审核拒绝',
  `audit_reason` VARCHAR(255) DEFAULT NULL COMMENT '审核拒绝原因',
  `audited_at` DATETIME DEFAULT NULL COMMENT '审核时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-正常',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credit_code` (`unified_social_credit_code`),
  KEY `idx_contact_phone` (`contact_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业表';

-- 会员等级表：会员等级配置（基础版 / VIP / SVIP）
CREATE TABLE `usr_member_levels` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '等级ID',
  `level_code` VARCHAR(20) NOT NULL COMMENT '等级编码：BASIC/VIP/SVIP',
  `level_name` VARCHAR(50) NOT NULL COMMENT '等级名称',
  `price` DECIMAL(10,2) NOT NULL COMMENT '价格（元/年）',
  `discount_rate` DECIMAL(3,2) NOT NULL COMMENT '折扣率（如0.80表示8折）',
  `monthly_meeting_hours` INT NOT NULL DEFAULT 0 COMMENT '每月免费会议室时长（小时）',
  `meeting_booking_advance_days` INT NOT NULL DEFAULT 0 COMMENT '会议室提前预约天数',
  `meeting_priority` TINYINT NOT NULL DEFAULT 0 COMMENT '会议室预约优先级：0-无, 1-普通, 2-优先',
  `meeting_overtime_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '会议室超出费用（元/小时）',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '权益描述',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_level_code` (`level_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员等级表';

-- 企业员工表：记录企业与其员工的关联关系
CREATE TABLE `usr_enterprise_members` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '记录ID（雪花）',
  `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `role` TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0-普通员工, 1-企业管理员',
  `invite_status` TINYINT NOT NULL DEFAULT 0 COMMENT '邀请状态：0-待接受, 1-已接受, 2-已拒绝',
  `invited_at` DATETIME DEFAULT NULL COMMENT '邀请时间',
  `accepted_at` DATETIME DEFAULT NULL COMMENT '接受时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enterprise_user` (`enterprise_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业员工表';

-- 会员购买记录表：记录企业购买会员服务包的记录
CREATE TABLE `usr_member_purchases` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '购买ID（雪花）',
  `purchase_no` VARCHAR(32) NOT NULL COMMENT '购买编号',
  `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
  `member_level_id` BIGINT UNSIGNED NOT NULL COMMENT '会员等级ID',
  `original_price` DECIMAL(10,2) NOT NULL COMMENT '原价',
  `pay_price` DECIMAL(10,2) NOT NULL COMMENT '实付价格',
  `payment_method` TINYINT NOT NULL DEFAULT 1 COMMENT '支付方式：1-微信支付, 2-余额支付',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '微信支付交易号',
  `out_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '商户订单号',
  `start_date` DATE NOT NULL COMMENT '开始日期',
  `end_date` DATE NOT NULL COMMENT '结束日期（1年）',
  `payment_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态：0-待支付, 1-支付成功, 2-支付失败, 3-已退款',
  `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_no` (`purchase_no`),
  KEY `idx_enterprise_id` (`enterprise_id`),
  KEY `idx_payment_status` (`payment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员购买记录表';

-- ---------- 种子数据（会员等级） ----------
INSERT INTO `usr_member_levels`
  (`level_code`, `level_name`, `price`, `discount_rate`, `monthly_meeting_hours`, `meeting_booking_advance_days`, `meeting_priority`, `meeting_overtime_fee`, `description`) VALUES
  ('BASIC', '基础版', 0.00, 0.95, 0, 0, 0, 80.00, '老板本人9折/95折'),
  ('VIP',   'VIP版',   5000.00, 0.90, 4, 2, 1, 80.00, '全公司员工8折/9折'),
  ('SVIP',  'SVIP版',  12000.00, 0.85, 8, 1, 2, 80.00, '全公司员工7折/85折');

-- 后台管理员表：管理端登录（/api/v1/auth/login，username + password），
-- 与小程序用户（usr_users，/api/v1/auth/wx-login）天然隔离
CREATE TABLE `usr_admins` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `username` VARCHAR(32) NOT NULL COMMENT '登录用户名',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码 bcrypt 哈希',
  `name` VARCHAR(64) NOT NULL COMMENT '姓名',
  `role` TINYINT NOT NULL DEFAULT 2 COMMENT '角色：1-超级管理员, 2-运营管理员',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台管理员表';

-- 种子数据：初始超级管理员（密码 123456，bcrypt）
INSERT INTO `usr_admins` (`username`, `password_hash`, `name`, `role`, `status`) VALUES
  ('admin', '$2a$10$ZUXdPnydoz4kKJQYT7aRw.rT9dhuPOgr6GySeCmwolTGl1r1LvdMO', '超级管理员', 1, 1);
