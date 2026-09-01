# 数据库迁移（db/migrations/）

增量迁移文件，与 `db/*.sql`（全量重建脚本）**双轨并行**：

- `db/*.sql`：开发期 `reset_db.sh` 全量重建用（可重复执行）；
- `db/migrations/`：增量演进基线（开发期由 `reset_db.sh` 末尾的 `schema_migrations` 标记已应用）；
- `src/main/resources/db/migration/`：**Flyway 迁移脚本（V1~V10）**，生产 profile
  （`SPRING_PROFILES_ACTIVE=prod`）启动时由 `FlywayMigrationRunner` 自动执行。
  注意：Spring Boot 4 已移除 Flyway 自动配置，本仓库用 Flyway Java API 驱动；
  复制到 Flyway 目录时需去掉 `-- +migrate Down` 回滚段（Flyway 整文件执行）。

**改表结构时必须同时更新两处**（`db/*.sql` 与 `db/migrations/NNN_*.sql`），
并同步 `src/main/resources/db/migration/Vx__*.sql`，保持三处最终形态一致。

## 文件格式

文件名 `NNN_description.sql`（NNN 为版本号，按编号顺序应用）：

```sql
-- 升级语句（自动按行尾分号切分，单事务执行）
CREATE TABLE ...

-- +migrate Down
-- 回滚语句（可省略；省略时该版本不可回滚）
DROP TABLE ...
```

> 约定：迁移文件内不允许出现函数体/存储过程等**含分号的复合语句**
> （执行器按行尾 ";" 切分）。新增表一律写到新文件，禁止修改已发布的迁移文件。

## 已发布迁移

| 版本 | 说明 |
|---|---|
| 001 | 1.0 基线：19 张表（带域前缀 `usr_`/`prd_`/`ord_`/`mtg_`/`trd_`/`sys_`）+ 会员等级/充值档位/商品/每日菜单/会议室种子数据 |
| 002 | 用户域：新增 usr_admins 后台管理员表 + 初始管理员种子（admin / 123456） |
| 003 | 主键 ID 策略：14 张业务表去掉 AUTO_INCREMENT（改雪花，见 db/README.md「主键 ID 策略」） |
| 004 | 交易域：trd_recharge_records 补充 paid_at 支付时间列（1.0 设计遗漏） |
| 005 | 会议室定价模型（1.1）：mtg_rooms 删除 hourly_fee、新增 mtg_room_level_prices、mtg_reservations 增加价格快照（overtime_unit_price / free_hours_deducted） |
| 006 | 交易可靠性（1.2）：新增 trd_payments 支付单 / trd_refunds 退款单 / ord_order_status_history 订单状态历史 / sys_idempotency 幂等记录（roadmap 1.2，编号/外部交易号/幂等键唯一） |
| 007 | 商品中心 SKU 化（1.3）：新增 prd_skus / prd_spec_groups / prd_spec_values；prd_products 增 product_status 状态生命周期；prd_categories 增 parent_id / icon_url / is_show；ord_order_items 增 SKU 快照四字段（roadmap 1.3） |
| 008 | 会议室资源化（1.4）：新增 mtg_bookings 资源占用表；mtg_reservations 增 order_id（预约订单化）+ 状态补「使用中」（0-待确认,1-已确认,2-使用中,3-已完成,4-已取消,5-已过期）；ord_orders.order_type 补 4-会议室；ord_order_status_history.biz_type 补 3-会议室订单（roadmap 1.4） |
| 009 | 账户/钱包（1.5）：新增 acct_accounts 账户表（余额唯一事实源，可用/赠送/冻结）；usr_users.balance/gift_balance 回填后删除（余额从用户资料剥离）；trd_balance_transactions 增 frozen_balance_before/after（冻结/解冻审计）（roadmap 1.5） |
| 010 | 运营/营销（1.6）：新增 mkt_coupons 优惠券模板 / mkt_user_coupons 用户券（领取快照）；ord_orders 增 coupon_id / coupon_name_snapshot / coupon_discount（优惠券快照）（roadmap 1.6） |

## 开发流程

- 开发期仍然 `./reset_db.sh`（全量重建，库名 `lease_db`；Flyway 默认禁用）；
- 已有数据的环境（含测试环境）接入迁移工具后一律增量升级，**禁止**改 `db/*.sql` 后重跑重建；
- **Flyway（已接入）**：改表时同步 `src/main/resources/db/migration/`（Flyway 命名
  `Vx__desc.sql`，去掉 `+migrate Down` 段），生产启动自动迁移；已验证全新库
  V1~V10 建 33 表 + 种子数据。
