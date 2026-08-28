# 数据库迁移（db/migrations/）

增量迁移文件，与 `db/*.sql`（全量重建脚本）**双轨并行**：

- `db/*.sql`：开发期 `reset_db.sh` 全量重建用（可重复执行）；
- `db/migrations/`：上线后增量演进用（Spring Boot 接入 Flyway 等迁移工具后由工具执行；
  目前开发期由 `reset_db.sh` 末尾的 `schema_migrations` 同步标记为已应用）。

**改表结构时必须同时更新两处**，保持最终形态一致。

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

## 开发流程

- 开发期仍然 `./reset_db.sh`（全量重建，库名 `lease_db`）；
- 已有数据的环境（含测试环境）接入迁移工具后一律增量升级，**禁止**改 `db/*.sql` 后重跑重建；
- 接入 Flyway 时：把本目录内容同步到 `src/main/resources/db/migration/`（按 Flyway
  命名规范 `V001__init.sql`），并配置 `spring.flyway.*` 数据源参数。
