#!/usr/bin/env bash
# ============================================================================
# 重置数据库：检查并创建数据库（不存在时）→ 清空所有表 → 按依赖顺序重建
# 用法：./reset_db.sh                        （默认库 lease_db，本机 3306）
#       DB_NAME=xxx ./reset_db.sh            （指定其他库）
#       MYSQL_PASSWORD=xxx ./reset_db.sh     （连接参数：HOST/PORT/USER/PASSWORD）
#
# 依赖关系：usr → prd → ord → mtg → trd → sys
#   - 删除：反向依赖顺序（先删被引用多的），SET FOREIGN_KEY_CHECKS=0 兜底
#   - 建表：循环执行 db/ 下按编号排序的域文件（编号即依赖顺序）
# ============================================================================
set -euo pipefail

DB_NAME="${DB_NAME:-lease_db}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}" # 默认本机 Homebrew MySQL root 密码，可用环境变量覆盖
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MYSQL=(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER")
[ -n "$MYSQL_PASSWORD" ] && MYSQL+=(-p"$MYSQL_PASSWORD")

echo "==> 检查数据库 $DB_NAME 是否存在..."
"${MYSQL[@]}" -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

echo "==> 清空 $DB_NAME 所有表..."
"${MYSQL[@]}" "$DB_NAME" <<'SQL'
SET FOREIGN_KEY_CHECKS = 0;
-- 反向依赖顺序删除（新增表时，按依赖关系补进对应位置；表名带域前缀 {域缩写}_）
DROP TABLE IF EXISTS sys_idempotency;               -- 系统域，1.2 幂等记录（无依赖）
DROP TABLE IF EXISTS sys_operation_logs;           -- 系统域，无依赖
DROP TABLE IF EXISTS sys_notifications;            -- 系统域，依赖 usr_users
DROP TABLE IF EXISTS ord_order_status_history;     -- 订单域，1.2 状态历史（无依赖）
DROP TABLE IF EXISTS ord_meal_reservation_items;   -- 订单域，依赖 ord_meal_reservations
DROP TABLE IF EXISTS ord_meal_reservations;        -- 订单域，依赖 usr_users / usr_enterprises / ord_orders / prd_products
DROP TABLE IF EXISTS ord_order_items;              -- 订单域，依赖 ord_orders / prd_products
DROP TABLE IF EXISTS ord_orders;                   -- 订单域，依赖 usr_users / usr_enterprises / prd_products
DROP TABLE IF EXISTS prd_skus;                     -- 商品域，1.3 SKU，依赖 prd_products
DROP TABLE IF EXISTS prd_spec_values;              -- 商品域，1.3 规格值，依赖 prd_spec_groups
DROP TABLE IF EXISTS prd_spec_groups;              -- 商品域，1.3 规格组，依赖 prd_products
DROP TABLE IF EXISTS prd_daily_menus;              -- 商品域，依赖 prd_products
DROP TABLE IF EXISTS prd_products;                 -- 商品域，依赖 prd_categories
DROP TABLE IF EXISTS prd_categories;               -- 商品域，无依赖
DROP TABLE IF EXISTS mtg_bookings;                  -- 会议室域，1.4 占用表，依赖 mtg_reservations
DROP TABLE IF EXISTS mtg_reservations;             -- 会议室域，依赖 mtg_rooms / usr_users / usr_enterprises
DROP TABLE IF EXISTS mtg_rooms;                    -- 会议室域，无依赖
DROP TABLE IF EXISTS acct_accounts;                -- 交易域，1.5 账户（= 用户ID），依赖 usr_users
DROP TABLE IF EXISTS trd_balance_transactions;     -- 交易域，依赖 usr_users / ord_orders
DROP TABLE IF EXISTS trd_refunds;                  -- 交易域，1.2 退款单，依赖 trd_payments
DROP TABLE IF EXISTS trd_payments;                 -- 交易域，1.2 支付单，依赖 usr_users
DROP TABLE IF EXISTS trd_recharge_records;         -- 交易域，依赖 usr_users / trd_recharge_tiers
DROP TABLE IF EXISTS trd_recharge_tiers;           -- 交易域，无依赖
DROP TABLE IF EXISTS usr_member_purchases;         -- 用户域，依赖 usr_enterprises / usr_member_levels
DROP TABLE IF EXISTS usr_enterprise_members;       -- 用户域，依赖 usr_enterprises / usr_users
DROP TABLE IF EXISTS usr_member_levels;            -- 用户域，无依赖
DROP TABLE IF EXISTS usr_enterprises;              -- 用户域，无依赖
DROP TABLE IF EXISTS usr_users;                    -- 用户域
DROP TABLE IF EXISTS usr_admins;                   -- 用户域，后台管理员（无依赖）
SET FOREIGN_KEY_CHECKS = 1;
SQL

echo "==> 按依赖顺序建表..."
# 新增域文件（db/NN_xxx.sql）会被自动纳入，无需改动脚本
# 域文件内的兜底 DROP 在清空后是空操作
for f in "$SCRIPT_DIR"/db/[0-9]*.sql; do
    "${MYSQL[@]}" "$DB_NAME" < "$f"
done

echo "==> 同步迁移状态（全量重建等价于已应用全部迁移）..."
# 与 db/migrations/ 双轨一致：把已发布迁移全部标记为已应用，
# 保证 reset 后接入迁移工具时 migrate-up 为空操作、status 全部已应用
"${MYSQL[@]}" "$DB_NAME" <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migrations (
    version    INT UNSIGNED NOT NULL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迁移版本记录';
SQL
for f in "$SCRIPT_DIR"/db/migrations/[0-9]*.sql; do
    version="$(basename "$f" | cut -d_ -f1)"
    name="$(basename "$f" | sed 's/^[0-9]*_//; s/\.sql$//')"
    "${MYSQL[@]}" "$DB_NAME" -e "INSERT IGNORE INTO schema_migrations (version, name) VALUES ($version, '$name');"
done

TABLES="$("${MYSQL[@]}" "$DB_NAME" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DB_NAME';")"
echo "==> 完成，当前共 ${TABLES} 张表"
