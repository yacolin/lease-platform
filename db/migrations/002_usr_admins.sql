-- ============================================================================
-- 002_usr_admins：后台管理员表（管理端登录）
-- 与 db/01_usr.sql 中 usr_admins 的最终形态保持一致（双轨并行）。
-- 种子数据：初始超级管理员 admin / 123456（bcrypt 哈希）。
-- ============================================================================

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

-- 种子数据：初始超级管理员（密码 123456）
INSERT INTO `usr_admins` (`username`, `password_hash`, `name`, `role`, `status`) VALUES
  ('admin', '$2a$10$ZUXdPnydoz4kKJQYT7aRw.rT9dhuPOgr6GySeCmwolTGl1r1LvdMO', '超级管理员', 1, 1);

-- +migrate Down
DROP TABLE IF EXISTS `usr_admins`;
