# 数据库建表脚本

按业务域拆分，每个域一个 SQL 文件，**按依赖顺序编号**；表名统一带域前缀 `{域缩写}_`（参照 beauty_salon 约定），数据库里一眼可辨归属。数据库：MySQL 8.x，库名 `lease_db`（`utf8mb4` / `utf8mb4_unicode_ci`）。

## 目录结构

| 文件 | 业务域 | 表 |
|---|---|---|
| `01_usr.sql` | 用户域 `usr_` | usr_users, usr_admins, usr_enterprises, usr_member_levels, usr_enterprise_members, usr_member_purchases |
| `02_prd.sql` | 商品域 `prd_` | prd_categories, prd_products, prd_daily_menus |
| `03_ord.sql` | 订单域 `ord_` | ord_orders, ord_order_items, ord_meal_reservations, ord_meal_reservation_items |
| `04_mtg.sql` | 会议室域 `mtg_` | mtg_rooms, mtg_room_level_prices, mtg_reservations |
| `05_trd.sql` | 交易域 `trd_` | trd_recharge_tiers, trd_recharge_records, trd_balance_transactions |
| `06_sys.sql` | 系统域 `sys_` | sys_notifications, sys_operation_logs |

> 说明：表结构遵照 1.0 版本共享对话的 MySQL 设计（20 张表，覆盖用户体系 /
> 充值系统 / 折扣系统 / 咖啡点单 / 正餐预订 / 会议室预约 / 商家后台），仅表名按域
> 加了前缀。主键 `BIGINT UNSIGNED AUTO_INCREMENT`（DDL 保留自增属性，实际 ID 生成
> 策略由 MyBatis-Plus 按表控制，见下「主键 ID 策略」），金额 `BIGINT`（整数分，
> 最小单位，全链路 DB/接口/微信支付统一以分为单位），
> 编号字段建唯一索引，外键字段建普通索引，业务表含 `created_at, updated_at`
> （纯流水表如 `trd_balance_transactions` / `ord_order_items` 只保留 `created_at`）。
> **建表与种子分离**：域文件只含 DDL（无 INSERT）；开发种子数据由 `db/seed.py` 生成
> （幂等可重跑，含与集成测试断言一致的核心数据 + 扩充演示数据），`make db-seed` 执行；
> 另外 `db/seed_dev_user.py` 在基础种子之上按需给开发登录用户 `mock_dev_user` 叠加一整套
> 演示数据（企业管理员 + VIP + 余额/订单/预订/会议室/优惠券/通知等），`make db-seed-dev` 执行，
> 仅供本地登录小程序查看数据，**跑集成测试前勿执行**（集成测试依赖 `mock_dev_user` 为干净新用户）；
> `db/seed_bulk.py` 再叠加一层**规模数据**（约 130 万行，让索引/多级缓存/布隆过滤器/定时清理
> 等性能特性可被真实观测），`make db-seed-bulk` 执行，同样**跑集成测试前勿执行**；
> 迁移（`migrations/`）仅保留 admin 初始化账号（生产必需），演示种子不进生产，
> 上生产只跑建表（`make db-init` / `reset_db.sh`），
> **切勿在生产执行 `db/seed.py` / `db/seed_dev_user.py` / `db/seed_bulk.py`**。

## 主键 ID 策略（2.0 共享对话决策 + usr_admins）

参照共享对话的 ID 方案（核心/资金/流水表用雪花，配置/枚举/字典表用自增），
对 20 张表统一决策如下。**实体类用 `@TableId` 显式声明，DDL 已同步落地**：
雪花表去掉 `AUTO_INCREMENT`（避免 DB 误生成小 ID 混入雪花空间），全量重建见
`db/0*.sql`，增量迁移见 `db/migrations/003_snowflake_ids.sql`：

| 策略 | 表 | 说明 |
|---|---|---|
| ✅ 雪花 `IdType.ASSIGN_ID` | usr_users | 根节点，被订单/交易/会议室/会员购买关联，最高优先级 |
| ✅ 雪花 | usr_enterprises | 企业主体，被用户/订单关联 |
| ✅ 雪花 | usr_enterprise_members | 关系表，存雪花 user_id/enterprise_id，统一类型 |
| ✅ 雪花 | usr_member_purchases | 购买记录，数据积累 |
| ✅ 雪花 | prd_products | 商品主表，被订单明细关联 |
| ✅ 雪花 | prd_daily_menus | 每日菜单按天生成，持续积累 |
| ✅ 雪花 | ord_orders | 数据量最大，未来必然分表 |
| ✅ 雪花 | ord_order_items | 订单子表 |
| ✅ 雪花 | ord_meal_reservations | 订餐预订，持续增长 |
| ✅ 雪花 | ord_meal_reservation_items | 订餐子项 |
| ✅ 雪花 | mtg_reservations | 预订记录，数据量可能不小 |
| ✅ 雪花 | trd_recharge_records | 资金流水，绝对不可重复 |
| ✅ 雪花 | trd_balance_transactions | 余额流水，资金相关 |
| ✅ 雪花 | sys_operation_logs | 海量日志，为按 ID 分表留后路 |
| ❌ 自增 `IdType.AUTO` | usr_member_levels | 会员等级配置，就几条 |
| ❌ 自增 | prd_categories | 分类配置，数据量小且稳定 |
| ❌ 自增 | mtg_rooms | 会议室配置 |
| ❌ 自增 | mtg_room_level_prices | 会议室等级定价覆盖配置 |
| ❌ 自增 | trd_recharge_tiers | 充值档位配置 |
| ❌ 自增 | sys_notifications | 通知模板/站内信，量小 |
| ❌ 自增 | **usr_admins** | **新增（管理端登录配套）：管理员账号量级极小、不被业务表外键关联（ID 仅出现在 JWT/会话），归账号配置类 → 自增** |

> 新增业务表默认雪花（全局 `mybatis-plus.global-config.db-config.id-type: assign_id`），
> 配置/枚举/账号类表显式 `@TableId(type = IdType.AUTO)`。

## 执行方式

**开发环境一键重置**（建表 + 灌开发种子，`Makefile` 合并为一步）：

```bash
make db-reset        # = ./reset_db.sh（建库/清空/建表） + python3 db/seed.py（灌种子）
make db-seed         # 仅重灌种子（幂等）；改了 db/seed.py 后重跑即可
```

**开发登录用户演示数据**（`mock_dev_user`，叠加在基础种子之上，按需执行）：

```bash
make db-seed-dev     # = python3 db/seed_dev_user.py（幂等叠加；须先 make db-seed / db-reset）
make db-reset-dev    # = ./reset_db.sh + db/seed.py + db/seed_dev_user.py（开发一步到位）
```

> `db/seed_dev_user.py` 会先按 openid / 统一社会信用代码清理 `mock_dev_user` 及其企业相关数据，
> 再插入固定主键（`990000000000000000 + n`）的演示数据，因此可重复执行且结果一致，也会覆盖
> 开发登录自动创建的裸用户。数据覆盖：企业/员工、会员、账户余额与流水、充值、咖啡订单、
> 正餐预订、会议室预约、优惠券、支付单/退款单、站内通知。
> ⚠️ 集成测试断言 `mock_dev_user` 为「干净新用户」，跑 `make test` 前请用 `make db-reset`
> 恢复基础种子（只跑 `db/seed.py`），不要叠加本脚本。

**规模数据**（`db/seed_bulk.py`，叠加在基础种子之上，按需执行）：

基础种子是「可断言的最小集」（4 分类 / 27 商品 / 0 订单 / 8 会议室 / 3 用户），
它的职责是给集成测试稳定的断言依据，**不是**让性能特性可验证。
而当前技术栈（复合索引 / L1+L2 多级缓存 / 布隆过滤器 / 定时过期清理 / 聚合统计 / 深分页）
在小数据量下全都看不出差别——优化器在几十行时会直接全表扫、布隆按 10 万量级设计却只装 27 个元素、
过期清理在 0 条积压时毫无意义。`seed_bulk.py` 用于补齐这一层：

```bash
make db-seed-bulk                    # 叠加约 130 万行（默认，约 25 秒）
make db-reset-bulk                   # 重建 + 基础种子 + 规模数据（一步到位）
make db-purge-bulk                   # 只清理规模数据，保留基础种子
SEED_ARGS="--scale 0.2" make db-seed-bulk   # 约 26 万行的快速小规模
SEED_ARGS="--scale 3"   make db-seed-bulk   # 压测用大规模
python3 db/seed_bulk.py --help       # 完整参数（--scale/--batch/--seed/--purge-only）
```

默认量级（`--scale 1.0`）：订单 20 万 / 订单明细 20 万 / 通知 30 万 / 会议预约 12 万 /
资金流水 15 万 / 支付单 8 万 / 餐预约 5 万 / 占用 4 万 / 商品 2 万 + SKU 2 万 / 用户 5000。

数据分布刻意贴近真实查询形态（对应 `docs/缓存与查询效率评估.md` 各条优化）：
订单 `created_at` 铺满近 365 天且约 1.5% 落在今天（让今日聚合与 GROUP BY 有量）；
1% 的「重度用户」承接约 1/4 订单（每人约 2000 单，使深分页可测）；
会议预约约 60% 为过去日期、其中多数早已终态、约 1/8 仍是待确认/已确认
（i.e. 定时清理任务有约 9000 条真实积压，远超单批 500，可验证分批与自排空）；
通知约 40% 未读；商品约 8% 下架、2% 逻辑删除。

**幂等与隔离**：叠加数据一律使用保留主键段 `[700000000000000000, 700000001000000000)`，
每次运行先按该段精确清理自己、再重灌，固定随机种子保证结果一致——
**绝不触碰基础种子的核心行**。（`sys_notifications` 是自增表，显式写大 id 会把
`AUTO_INCREMENT` 顶高，故该表不指定 id，改用 `title` 的 `[BULK]` 前缀作清理标记。）

> ⚠️ 与 `db/seed_dev_user.py` 同类，**跑集成测试前请先 `make db-reset`**：
> 规模数据会让「精确计数」类断言失败（如通知总数、`sys_operation_logs==1` 等）。
> 只想去掉规模数据而保留基础种子时用 `make db-purge-bulk`。

**仅初始化数据库结构**（生产环境用这个，**不灌种子**，谨慎执行）：

```bash
./reset_db.sh                        # 默认库 lease_db
DB_NAME=xxx ./reset_db.sh            # 指定其他库
MYSQL_PASSWORD=xxx ./reset_db.sh     # 连接参数可用 MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD 覆盖
```

> `reset_db.sh` 重建数据库后会**一并清理 Redis 中的应用缓存**（`cache:*` 前缀）：
> 否则运行中的应用会继续返回 L1/L2 里的旧参照数据（最长 30 分钟）。
> 只删本项目的 `cache:*`，不动 `auth:refresh:*` 等其它键。

> 种子脚本连接参数与 `reset_db.sh` 一致（`DB_NAME` / `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD`），
> 也可用 `--db/--host/--port/--user/--password` 命令行参数覆盖。

单独重建某个域（不清空其他域，该域文件内的 DROP 只清自己的表）：

```bash
mysql -u root -p lease_db < db/06_sys.sql
```

每个域文件都以 `DROP TABLE IF EXISTS ...` 开头，可重复执行，改表后重跑一遍即可。

## 规范

- **新增表**：写入所属业务域文件，按 `DROP + CREATE + 索引 + COMMENT` 的顺序组织；新域则新建 `db/NN_xxx.sql`（两位编号即依赖顺序，`reset_db.sh` 自动纳入）；同时在 `reset_db.sh` 的删除列表中按反向依赖补上对应 DROP
- **删除表**：从所属域文件删掉对应 DDL，删除后如无其他表引用，重跑脚本即生效
- **编号顺序** = 依赖顺序（被引用的表先建），改文件时必须保持 引用方向 与 编号方向 一致：`usr → prd → ord → mtg → trd → sys`
- **命名约定**：表名带域前缀 `{域缩写}_`（与所属域文件一致：`usr_` / `prd_` / `ord_` / `mtg_` / `trd_` / `sys_`），新表先定所属域再定前缀；金额一律 `BIGINT`（整数分，最小单位，与微信支付对齐）；业务编号（`order_no` / `reservation_no` / `purchase_no` / `out_trade_no`）建唯一索引；外键字段建普通索引
- **改表结构**：同步更新 `db/migrations/` 对应迁移（双轨并行，见 `db/migrations/README.md`）
