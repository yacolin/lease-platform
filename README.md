# 租赁小程序平台（lease-platform）

基于 **Java 21 + Spring Boot 4 + MyBatis-Plus + MySQL + Redis** 的园区/企业服务平台。1.0 版本围绕企业员工的日常消费场景：**咖啡点单、正餐预订（每日菜单）、会议室预约、充值余额与会员折扣**，覆盖微信小程序（用户端）与管理后台（商家端）。

> 当前状态：**数据库设计（20 张表）+ 基础架构 + prd 商品域 + P0/P0+ 认证域已落地**（公开浏览可用；后台管理员登录 admin/123456 + 微信登录（开发 mock）+ JWT 认证 + 我的资料可用；管理端接口按 user_type=1 隔离，已可联调），后续按依赖顺序推进（企业 → 交易 → 订单 → 会议室 → 系统）。

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
make test            # 运行 49 例测试（集成测试需先 make db-reset）
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

> 小程序用户（user_type=2/3）访问管理端接口返回 403（类型隔离）。

### 公开/小程序端

**公开浏览（`/api/v1/public/**`，白名单放行，无需登录）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/public/categories` | 商品分类列表（仅启用） |
| GET | `/api/v1/public/products` | 商品分页（仅上架；`categoryId`/`productType` 筛选） |
| GET | `/api/v1/public/products/{id}` | 商品详情（下架视为 404） |
| GET | `/api/v1/public/menus?date=` | 每日菜单（缺省今天，含套餐名） |

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

### 接口分组与前端请求文件生成

springdoc 按端输出独立 OpenAPI JSON（`/v3/api-docs/{group}`，Security 白名单已放行）：

| 组 | 覆盖接口 | OpenAPI JSON | 前端产物 |
|---|---|---|---|
| `admin` | 管理员登录/登出 + 分类/商品/菜单管理 | `/v3/api-docs/admin` | 管理后台请求文件（如 `api/admin/*.js`） |
| `public` | 公开浏览 + 微信登录/刷新/登出 + 我的 | `/v3/api-docs/public` | 小程序请求文件（如 `api/miniprogram/*.js`） |
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
make test     # 91 例：service 单元（Mockito）+ controller Web（@WebMvcTest + 真实 Security 链）+ 集成（真实 MySQL + Redis）
```

集成测试基于 `db/02_prd.sql` 的固定种子数据断言（4 分类 / 8 商品 / 2026-08-30 菜单 11 条）与 `usr_admins` 种子（admin/123456），运行前需 `make db-reset`。

## 开发进度

- [x] 项目骨架、关键配置、统一响应/异常/分页
- [x] 数据库设计内附（20 表 + 种子 + 迁移 + reset 脚本）
- [x] prd 商品域：公开浏览 + 管理端 CRUD + 49 例测试 + Swagger 文档
- [x] P0 认证域：JWT 过滤器 / 微信登录（mock 固定 openid）/ 刷新登出（Redis 复合身份）/ 我的资料
- [x] P0+ 管理端登录：usr_admins 账号表 + /api/v1/auth/login（bcrypt）+ 管理端接口按 user_type=1 隔离，累计 91 例测试
- [ ] 企业域（实名注册审核 / 员工管理 / 会员购买）与交易域（充值 / 余额账本 / 支付回调）
- [ ] 订单域（咖啡点单 / 正餐预订）与会议室域（预约）
- [ ] 系统域（通知 / 操作日志）与工程化收尾（dev/prod 分离、Flyway、Docker）
