# 数据库建表脚本

按业务域拆分，每个域一个 SQL 文件，**按依赖顺序编号**；表名统一带域前缀 `{域缩写}_`（参照 beauty_salon 约定），数据库里一眼可辨归属。数据库：MySQL 8.x，库名 `lease_db`（`utf8mb4` / `utf8mb4_unicode_ci`）。

## 目录结构

| 文件 | 业务域 | 表 |
|---|---|---|
| `01_usr.sql` | 用户域 `usr_` | usr_users, usr_enterprises, usr_member_levels, usr_enterprise_members, usr_member_purchases |
| `02_prd.sql` | 商品域 `prd_` | prd_categories, prd_products, prd_daily_menus |
| `03_ord.sql` | 订单域 `ord_` | ord_orders, ord_order_items, ord_meal_reservations, ord_meal_reservation_items |
| `04_mtg.sql` | 会议室域 `mtg_` | mtg_rooms, mtg_reservations |
| `05_trd.sql` | 交易域 `trd_` | trd_recharge_tiers, trd_recharge_records, trd_balance_transactions |
| `06_sys.sql` | 系统域 `sys_` | sys_notifications, sys_operation_logs |

> 说明：表结构遵照 1.0 版本共享对话的 MySQL 设计（19 张表，覆盖用户体系 /
> 充值系统 / 折扣系统 / 咖啡点单 / 正餐预订 / 会议室预约 / 商家后台），仅表名按域
> 加了前缀。主键 `BIGINT UNSIGNED AUTO_INCREMENT`，金额 `DECIMAL(10,2)`（元），
> 编号字段建唯一索引，外键字段建普通索引，业务表含 `created_at, updated_at`
> （纯流水表如 `trd_balance_transactions` / `ord_order_items` 只保留 `created_at`）。
> 种子数据共五处：`01_usr.sql` 底部（会员等级）、`02_prd.sql` 底部（商品分类 /
> 商品 / 每日菜单）、`04_mtg.sql` 底部（会议室）、`05_trd.sql` 底部（充值档位）。

## 执行方式

**一键重置**（每次运行：检查并创建数据库（不存在时）→ 清空所有表 → 按依赖顺序重建）：

```bash
./reset_db.sh                        # 默认库 lease_db
DB_NAME=xxx ./reset_db.sh            # 指定其他库
MYSQL_PASSWORD=xxx ./reset_db.sh     # 连接参数可用 MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD 覆盖
```

单独重建某个域（不清空其他域，该域文件内的 DROP 只清自己的表）：

```bash
mysql -u root -p lease_db < db/06_sys.sql
```

每个域文件都以 `DROP TABLE IF EXISTS ...` 开头，可重复执行，改表后重跑一遍即可。

## 规范

- **新增表**：写入所属业务域文件，按 `DROP + CREATE + 索引 + COMMENT` 的顺序组织；新域则新建 `db/NN_xxx.sql`（两位编号即依赖顺序，`reset_db.sh` 自动纳入）；同时在 `reset_db.sh` 的删除列表中按反向依赖补上对应 DROP
- **删除表**：从所属域文件删掉对应 DDL，删除后如无其他表引用，重跑脚本即生效
- **编号顺序** = 依赖顺序（被引用的表先建），改文件时必须保持 引用方向 与 编号方向 一致：`usr → prd → ord → mtg → trd → sys`
- **命名约定**：表名带域前缀 `{域缩写}_`（与所属域文件一致：`usr_` / `prd_` / `ord_` / `mtg_` / `trd_` / `sys_`），新表先定所属域再定前缀；金额一律 `DECIMAL(10,2)`（元）；业务编号（`order_no` / `reservation_no` / `purchase_no` / `out_trade_no`）建唯一索引；外键字段建普通索引
- **改表结构**：同步更新 `db/migrations/` 对应迁移（双轨并行，见 `db/migrations/README.md`）
