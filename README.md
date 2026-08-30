# 租赁小程序平台（lease-platform）

基于 **Java 21 + Spring Boot 4 + MyBatis-Plus + MySQL + Redis** 的园区/企业服务平台。1.0 版本围绕企业员工的日常消费场景：**咖啡点单、正餐预订（每日菜单）、会议室预约、充值余额与会员折扣**，覆盖微信小程序（用户端）与管理后台（商家端）。

> 当前状态：**1.0 全量落地（20 张表 + P0~P6 全部完成）**：认证/企业/交易/咖啡点单/正餐预订/会议室/系统域（通知、操作日志）+ 工程化（dev/prod 环境、Flyway 自动迁移、Docker 部署）。管理员 admin/123456；微信登录（开发 mock）；核心流程全链路可用。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言/框架 | Java 21 + Spring Boot 4.1.1 |
| 数据库访问 | MyBatis-Plus 3.5.17（`mybatis-plus-spring-boot4-starter`，分页插件独立模块；业务表雪花 ID、配置表自增，见 `db/README.md`） |
| 数据库 | MySQL 8（`lease_db`，表结构由 SQL 文件与迁移双轨管理） |
| 缓存 | Redis（Lettuce + commons-pool2，预留 refresh token 会话） |
| 安全 | Spring Security（无状态 API + 白名单 + 管理端隔离 + CORS + JWT Bearer 认证过滤器） |
| JSON | Jackson 3（Spring Boot 4 默认，`tools.jackson.*`；注解仍在 `com.fasterxml.jackson.annotation`） |
| API 文档 | springdoc-openapi 3.1.0（Swagger UI，适配 Spring Boot 4） |
| 金额 | `BigDecimal`（DECIMAL(10,2)） |
| 构建 | Maven（wrapper `mvnw`）+ Makefile |

## 目录结构

```
lease-platform/
├── src/main/java/com/example/leaseplatform/
│   ├── common/            # 公共层：统一响应 / 错误码 / 业务异常 / 全局异常 / 分页 / 时间工具 / MP 配置
│   ├── config/            # 配置类：Security（白名单+CORS）/ JWT / 微信 / Security 属性 / OpenAPI
│   ├── security/          # JWT 认证：JwtTokenProvider / JwtAuthenticationFilter / LoginUser / UserContext
│   ├── prd/               # 商品域（按域分包，usr/ord/mtg/trd/sys 同构）
│       ├── entity/        # MyBatis-Plus 实体（PrdCategory/PrdProduct/PrdDailyMenu）
│       ├── mapper/        # MyBatis-Plus Mapper
│       ├── dto/           # 请求/响应对象（Create/Update Req + VO）
│       ├── service/       # 业务服务
│       └── controller/    # PrdPublicController（公开）/ PrdAdminController（管理端）
├── src/main/resources/
│   ├── application.yml    # 关键配置：数据源/Redis/MyBatis-Plus/微信登录/JWT/Security
│   └── (db/migration 预留：Flyway 接入路径，见 db/migrations/README.md)
├── db/                    # 建表 SQL（按业务域拆分，见 db/README.md）
│   └── migrations/        # 增量迁移基线（001_init + 002_usr_admins，20 张表 + 种子数据）
├── src/test/              # 91 例测试：service 单元 + controller Web + 真实 MySQL/Redis 集成
├── reset_db.sh            # 一键重置数据库（建库 → 清空 → 按依赖顺序建表）
└── Makefile               # 常用命令入口
```

## 快速开始

依赖：JDK 21、本地 MySQL 8（Homebrew，root 密码默认 `123456`）、Redis（本机 6379，无密码）。

```bash
make db-reset        # 建库 lease_db（不存在时）→ 清空 → 按依赖顺序建 20 张表 + 种子数据
make run             # 启动项目（前台，端口 8080）
make test            # 运行 281 例测试（集成测试需先 make db-reset）
make help            # 全部命令：run/stop/compile/test/build/run-jar/db-reset
```

启动后访问：
- **Swagger 文档**：http://localhost:8080/swagger-ui.html
- **公开接口示例**：`GET /api/v1/public/products`（商品列表）、`GET /api/v1/public/menus?date=2026-08-30`（每日菜单）

> 说明：Maven 本地仓库重定向到工作区 `.m2home/`（已 gitignore），受限环境（无法写 `~/.m2`）与正常开发均适用。

## 接口一览

> 完整约定见 **`docs/接口规范.md`**（路径/双轨模式/权限分层/响应错误码/新增接口流程）。

统一响应：`{code, message, data}`（code=0 成功）；分页：`data: {total, list}`；时间字段为 epoch 毫秒数字。
认证方式：请求头 `Authorization: Bearer <accessToken>`；未携带/无效 token 访问受保护接口返回 403。

接口按端分组（springdoc group-configs，见「接口分组与前端请求文件生成」）：
**管理端**（admin token）与 **公开/小程序端**（公开浏览 + 登录用户）。

### 管理端（`/api/v1/**`，仅 `user_type=1` 管理员 token 可访问，路径见 `lease.security.admin-paths`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/login` | 后台管理员登录 `{username, password}`（bcrypt 校验 usr_admins；种子 admin/123456；签发 user_type=1 的 token） |
| POST | `/api/v1/auth/logout` | 登出 `{refreshToken}`（作废 Redis 会话，管理端/小程序共用） |
| POST | `/api/v1/categories` | 创建分类 |
| GET | `/api/v1/categories` | 分类分页列表 |
| GET/PUT/DELETE | `/api/v1/categories/{id}` | 分类详情 / 更新 / 删除 |
| POST | `/api/v1/products` | 创建商品 |
| GET | `/api/v1/products` | 商品分页列表（分类/类型/上下架/关键词筛选） |
| GET/PUT/DELETE | `/api/v1/products/{id}` | 商品详情 / 更新 / 删除 |
| PUT | `/api/v1/products/{id}/status` | 商品上下架 |
| POST | `/api/v1/menus` | 创建菜单项 |
| GET | `/api/v1/menus` | 菜单分页列表 |
| GET/PUT/DELETE | `/api/v1/menus/{id}` | 菜单项详情 / 更新 / 删除 |
| GET | `/api/v1/enterprises` | 企业分页（审核状态/名称/信用代码/联系人筛选） |
| GET | `/api/v1/enterprises/{id}` | 企业详情 |
| PUT | `/api/v1/enterprises/{id}/audit` | 审核企业（1-通过, 2-拒绝；拒绝必填原因） |
| POST | `/api/v1/recharge-tiers` | 创建充值档位（重复充值金额 409） |
| GET | `/api/v1/recharge-tiers` | 充值档位分页列表 |
| GET/PUT/DELETE | `/api/v1/recharge-tiers/{id}` | 档位详情 / 更新 / 删除 |

> 小程序用户（user_type=2/3）访问管理端接口返回 403（类型隔离）。

### 公开/小程序端

**公开浏览（`/api/v1/public/**`，白名单放行，无需登录）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/public/categories` | 商品分类列表（仅启用） |
| GET | `/api/v1/public/products` | 商品分页（仅上架；`categoryId`/`productType` 筛选） |
| GET | `/api/v1/public/products/{id}` | 商品详情（下架视为 404） |
| GET | `/api/v1/public/menus?date=` | 每日菜单（缺省今天，含套餐名） |
| GET | `/api/v1/public/member-levels` | 会员等级列表（仅启用） |
| GET | `/api/v1/public/recharge-tiers` | 充值档位列表（仅启用） |

**认证（`/api/v1/auth/**`，白名单放行）**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/wx-login` | 微信小程序登录 `{code}`（开发 mock 固定复用 `mock_dev_user`；签发 user_type=2/3 的 token） |
| POST | `/api/v1/auth/refresh` | 刷新令牌 `{refreshToken}`（轮换，旧 refresh 作废） |
| POST | `/api/v1/auth/logout` | 登出 `{refreshToken}`（作废 Redis 会话） |

**我的（`/api/v1/me`，需登录）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/me` | 我的资料（昵称/头像/手机号 + 余额 + 会员等级） |
| PUT | `/api/v1/me` | 更新资料（昵称/头像/手机号） |

**我的企业（`/api/v1/me/enterprise/**`，需登录；员工管理/购买需企业管理员）**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/me/enterprise` | 企业实名注册（注册人即企业管理员，待审核） |
| GET | `/api/v1/me/enterprise` | 我的企业资料（审核状态/会员等级，过期惰性降级） |
| GET | `/api/v1/me/enterprise/members` | 企业员工列表 |
| POST | `/api/v1/me/enterprise/members` | 邀请员工（按手机号，企业须已通过审核） |
| DELETE | `/api/v1/me/enterprise/members/{userId}` | 移除员工（需先取消其管理员身份） |
| PUT | `/api/v1/me/enterprise/members/{userId}/admin` | 设置/取消企业管理员 |
| GET | `/api/v1/me/enterprise/invites` | 我的待处理邀请 |
| POST | `/api/v1/me/enterprise/invites/{id}/accept` | 接受邀请（企业须已通过审核） |
| POST | `/api/v1/me/enterprise/invites/{id}/reject` | 拒绝邀请 |
| POST | `/api/v1/me/enterprise/member-purchases` | 会员购买下单（待支付） |
| POST | `/api/v1/me/enterprise/member-purchases/{id}/mock-pay` | 开发 mock 支付（立即生效；P2 由支付回调替代） |
| GET | `/api/v1/me/enterprise/member-purchases` | 我的企业购买记录 |

**我的充值/余额（`/api/v1/me/**`，需登录）**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/me/recharge` | 充值下单 `{tierId}`（微信支付未配置时返回记录，走 mock-pay） |
| POST | `/api/v1/me/recharge/{id}/mock-pay` | 开发 mock 直充（立即入账；幂等） |
| POST | `/api/v1/me/recharge/{id}/query` | 主动查单兜底（已配置微信支付时同步微信侧状态） |
| GET | `/api/v1/me/recharge/records` | 我的充值记录 |
| GET | `/api/v1/me/balance-transactions` | 我的余额流水（充值/消费/退款，含赠送余额） |

**微信支付回调（`POST /api/v1/wx/payments/notify`，白名单，微信服务器调用）**：V3 回调解密入账（幂等）；开发环境未配置微信支付时返回失败，走 mock-pay 直充。

**我的订单（咖啡点单，`/api/v1/me/orders/**`，需登录）**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/me/orders` | 咖啡下单 `{items:[{productId, quantity, spec}]}`（折扣叠加后待支付） |
| POST | `/api/v1/me/orders/{id}/pay` | 余额支付（赠送余额优先扣；生成取餐码 → 待取餐） |
| POST | `/api/v1/me/orders/{id}/cancel` | 取消订单（待取餐取消原路退款） |
| GET | `/api/v1/me/orders` | 我的订单分页（状态筛选） |
| GET | `/api/v1/me/orders/{id}` | 订单详情（含明细/规格快照） |

**订单管理（`/api/v1/orders/**`，仅管理员）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/orders` | 订单分页（订单号/状态/日期筛选） |
| GET | `/api/v1/orders/{id}` | 订单详情 |
| PUT | `/api/v1/orders/{id}/status` | 状态推进（1→2→3；1/2→5 退款） |
| POST | `/api/v1/orders/verify-pickup` | 取餐码核销（待取餐/制作中 → 完成） |
| GET | `/api/v1/orders/stats` | 订单统计（今日订单/金额/待取餐/制作中） |

**折扣规则（P3/P4）**：应付 = 商品原价 × **会员折扣率**（usr_member_levels.discount_rate，非会员不打折）× **充值折扣率**（最近一次成功充值档位的 equivalent_discount，未充值不打折）；`member_discount` / `recharge_discount` 分别记录两项优惠金额。

**每日菜单运营（`/api/v1/menus/**`，仅管理员，P4）**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/menus/batch` | 整单配置（覆盖指定日期全部菜品） |
| POST | `/api/v1/menus/copy` | 复制整单（sourceDate → targetDate，目标先清空） |
| DELETE | `/api/v1/menus?date=` | 按日期清空菜单 |

**我的正餐预订（`/api/v1/me/meal-reservations/**`，需登录，P4）**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/me/meal-reservations` | 预订 `{productId, menuDate, timeSlot, quantity, deliveryType}`（规则：提前 1 天/晚 8 点截止/可订未来 3 天；套餐+菜品快照；关联订单） |
| POST | `/api/v1/me/meal-reservations/{id}/pay` | 余额支付（赠送余额优先扣 → 待备餐） |
| POST | `/api/v1/me/meal-reservations/{id}/cancel` | 取消（已支付原路退款） |
| GET | `/api/v1/me/meal-reservations` | 我的预订分页 |
| GET | `/api/v1/me/meal-reservations/{id}` | 预订详情（含当天菜品快照） |

**正餐预订管理（`/api/v1/meal-reservations/**`，仅管理员）**：分页（日期/状态筛选）、详情、`PUT /{id}/status` 备餐流转（1→2→3；1/2→5 退款，同步关联订单）。配送费：周边配送 5 元，自取/楼内 0。

**会议室（P5）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/public/rooms` | 可预约会议室列表（公开） |
| POST/GET/PUT/DELETE | `/api/v1/rooms/**` | 会议室管理（管理端 CRUD） |
| POST | `/api/v1/me/meeting-reservations` | 预约 `{roomId, reservationDate, startTime, endTime, meetingTopic}`（冲突 409；免费时长抵扣+超时计费） |
| POST | `/api/v1/me/meeting-reservations/{id}/pay` | 余额支付（待确认 → 已确认） |
| POST | `/api/v1/me/meeting-reservations/{id}/cancel` | 取消（已付费原路退款） |
| GET | `/api/v1/me/meeting-reservations` | 我的预约分页 |
| GET | `/api/v1/me/meeting-reservations/free-hours?month=` | 指定月份剩余免费时长（缺省当月） |
| GET | `/api/v1/meeting-reservations/**` | 预约管理（管理端：分页/详情/确认完成） |

**会议室计费规则（P5）**：可预约最早明天、最远 7 天、时段 08:00~22:00；同会议室/同日期时段重叠 → 409。企业会员免费时长（`usr_member_levels.monthly_meeting_hours`，VIP 4h/月、SVIP 8h/月）按**预约月**统计已用并优先抵扣；超出部分 × 会议室 `hourly_fee`（元/小时）计费（余额支付，赠送余额优先扣）。状态：待确认（付费）→ 已确认 → 已完成/已取消（退款）/已过期（惰性，查询时置过期且不退）。

### 接口分组与前端请求文件生成

springdoc 按端输出独立 OpenAPI JSON（`/v3/api-docs/{group}`，Security 白名单已放行）：

| 组 | 覆盖接口 | OpenAPI JSON | 前端产物 |
|---|---|---|---|
| `admin` | 管理员登录/登出 + 分类/商品/菜单管理 + 企业审核 + 充值档位 + 订单/正餐预订 + 会议室 | `/v3/api-docs/admin` | 管理后台请求文件（如 `api/admin/*.js`） |
| `public` | 公开浏览 + 微信登录/刷新/登出 + 我的 + 我的企业/充值 | `/v3/api-docs/public` | 小程序请求文件（如 `api/miniprogram/*.js`） |
| 全部 | 所有接口 | `/v3/api-docs` | Swagger UI 顶部按端切换 |

生成方式：按组拉取 JSON 后按模块拆分为 `api/*.js`，每个文件顶部注明后端契约路径
（如 `// 后端契约：POST /api/v1/auth/wx-login`），配合统一请求封装 `utils/request.js`
（BASE_URL + token 注入 + 401 自动重新登录 + 统一响应解包）。示例可参照小程序端
既有工程的 `utils/request.js + api/auth.js + api/order.js` 组织方式。

## 数据库设计（1.0）

按业务域拆分 6 个域文件、20 张表（表名带域前缀，参照 beauty_salon 约定）：

| 域（前缀） | 表 |
|---|---|
| 用户域 `usr_` | usr_users, usr_admins, usr_enterprises, usr_member_levels, usr_enterprise_members, usr_member_purchases |
| 商品域 `prd_` | prd_categories, prd_products, prd_daily_menus |
| 订单域 `ord_` | ord_orders, ord_order_items, ord_meal_reservations, ord_meal_reservation_items |
| 会议室域 `mtg_` | mtg_rooms, mtg_reservations |
| 交易域 `trd_` | trd_recharge_tiers, trd_recharge_records, trd_balance_transactions |
| 系统域 `sys_` | sys_notifications, sys_operation_logs |

- `db/`：全量重建脚本（可重复执行，`make db-reset` 一键跑）
- `db/migrations/`：增量迁移基线（`001_init`），改表结构两处同步
- 完整规范见 `db/README.md` 与 `db/migrations/README.md`

## 关键配置与安全

`src/main/resources/application.yml` 集中管理关键配置项，**生产环境务必用环境变量覆盖**：

| 配置 | 开发默认 | 说明 |
|---|---|---|
| 数据源 | `jdbc:mysql://127.0.0.1:3306/lease_db`，root/123456 | 密码用环境变量覆盖 |
| Redis | 127.0.0.1:6379，无密码 | 有密码时配置 `spring.data.redis.password`；存 refresh token 会话（`auth:refresh:{userType}:{userId}`，复合身份防串号） |
| 微信登录 | mock 模式（`wechat.mock-enabled=true`，固定复用 `mock-openid`，默认 `mock_dev_user`） | 生产填 appid/secret 并关闭 mock；勿用一次性 code 拼 openid |
| JWT | 内置 Base64 密钥（384 bit，HS384） | 生产用 `JWT_SECRET` 覆盖；Access 2h / Refresh 7d |
| Security | 白名单 `/api/v1/public/**`、`/api/v1/auth/**`、Swagger、健康检查；管理端隔离 `admin-paths`；CORS `*` | 生产收紧来源域名；新增管理端接口时补入 `admin-paths` |

## 测试

```bash
make test     # 281 例：service 单元（Mockito）+ controller Web（@WebMvcTest + 真实 Security 链）+ 集成（真实 MySQL + Redis）
```

集成测试基于 `db/02_prd.sql` 的固定种子数据断言（4 分类 / 8 商品 / 2026-08-30 菜单 11 条）与 `usr_admins` 种子（admin/123456），运行前需 `make db-reset`。

## 开发进度

- [x] 项目骨架、关键配置、统一响应/异常/分页
- [x] 数据库设计内附（20 表 + 种子 + 迁移 + reset 脚本）
- [x] prd 商品域：公开浏览 + 管理端 CRUD + 49 例测试 + Swagger 文档
- [x] P0 认证域：JWT 过滤器 / 微信登录（mock 固定 openid）/ 刷新登出（Redis 复合身份）/ 我的资料
- [x] P0+ 管理端登录：usr_admins 账号表 + /api/v1/auth/login（bcrypt）+ 管理端接口按 user_type=1 隔离，累计 281 例测试
- [x] P1 企业域：实名注册（注册人即管理员）/ admin 审核 / 员工邀请接受移除/管理员设置 / 会员等级购买（mock 支付生效）
- [x] P2 交易域：充值档位（public+admin）/ 充值下单 + mock 直充（事务幂等）/ 余额账本（credit/debit 流水）/ 微信支付回调+查单（结构就绪，未配置降级）
- [x] P3 咖啡订单：商品+规格快照下单 / 折扣叠加（会员×充值）/ 余额支付（赠送优先扣）+ 取餐码核销 / 状态流转 / 商家订单管理+统计
- [x] P4 正餐预订：菜单整单配置/复制 / 按日期+时段预订（规则校验+菜品快照）/ 备餐流转 / 折扣复用 DiscountCalculator
- [x] P5 会议室：public+admin 管理 / 预约冲突校验 / 会员免费时长抵扣+超时计费 / 状态流转（待确认→已确认→完成/取消/过期）
- [x] P6 系统域与工程化：通知中心 / 操作日志（AOP）/ dev-prod 环境分离 / Flyway 自动迁移 / Docker 部署

## 部署（Docker）

```bash
# 1. 配置环境变量（DB 密码、JWT 密钥等）
cp .env.example .env   # 按需填写

# 2. 一键启动 MySQL + Redis + 应用（prod profile，Flyway 自动建表 + 种子）
docker compose up -d --build

# 3. 访问
#    API 文档：http://localhost:8080/swagger-ui.html
#    管理端登录：admin / <DB_PASSWORD 同默认 123456>
```

- `Dockerfile`：多阶段构建（Maven 打包 → JRE 运行，prod profile）
- `docker-compose.yml`：mysql:8 + redis:7 + app（健康检查、环境变量注入）
- `db/init/01_create_db.sql`：MySQL 首次启动自动建库
- 应用启动时 `FlywayMigrationRunner` 自动执行 V1~V4 迁移（20 张表 + 种子数据）

## 环境分离

- `application-dev.yml`：开发（mock 微信登录、SQL 日志、Flyway 关闭）
- `application-prod.yml`：生产（DB/Redis/JWT/微信 全部环境变量注入、微信 mock 关闭、Flyway 启用）
- 启动：开发 `make run`；生产 `SPRING_PROFILES_ACTIVE=prod java -jar app.jar`（或 docker compose）
- [ ] 系统域（通知 / 操作日志）与工程化收尾（dev/prod 分离、Flyway、Docker）
