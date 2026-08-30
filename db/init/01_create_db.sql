# ============================================================================
# 初始化建库（MySQL 首次启动挂载 /docker-entrypoint-initdb.d 自动执行）
# 应用以 prod profile + Flyway 自动建表与种子数据，这里只负责创建数据库。
# ============================================================================
CREATE DATABASE IF NOT EXISTS `lease_db`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
