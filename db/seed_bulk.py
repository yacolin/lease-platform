#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# ============================================================================
# 开发用「规模种子」叠加脚本（dev-only）
# ----------------------------------------------------------------------------
# 为什么需要：db/seed.py 灌的是「可断言的最小集」（4 分类 / 27 商品 / 0 订单 /
#   8 会议室 / 3 用户），它的职责是让集成测试有稳定断言依据，**不是**让性能特性可验证。
#   而当前这套技术栈（复合索引 / L1+L2 多级缓存 / 布隆过滤器 / 定时过期清理 /
#   聚合统计 / 深分页）在小数据量下全都「看不出差别」——
#     · 优化器在几十行时会直接全表扫，索引用不上；
#     · 布隆过滤器按 10 万量级设计，27 个元素只占万分之三；
#     · 过期清理的「自排空 + 分批」在 0 条积压时毫无意义；
#     · 聚合统计 / 未读数 / 深分页都退化成常量时间。
#   本脚本在 seed.py 之上叠加**贴近真实分布的量级数据**，让上述能力可被真实观测。
#
# 与 seed.py 的关系（重要）：
#   - **叠加，不替换**：不改动、不删除 seed.py 的核心行（测试断言依赖它们）；
#   - **可重复执行**：每次运行先按「保留 id 段」清掉自己上次灌的数据，再重灌，
#     结果确定（固定随机种子），与 seed.py 的幂等约定一致；
#   - **跑集成测试前勿执行**：与 db/seed_dev_user.py 同类，它会让「精确计数」类
#     断言（如 sys_operation_logs==1、通知总数==N）失败。
#     测试前请 `make db-reset` 回到纯净种子。
#
# 数据分布刻意贴近查询形态（对应 docs/缓存与查询效率评估.md 的各条优化）：
#   · ord_orders        created_at 铺满近 365 天 + 约 1.5% 落在「今天」→ 命中 idx_created、
#                       让 /orders/stats 的今日聚合与 GROUP BY 真正有量；
#                       order_status 覆盖全状态，其中 1/2 为运营队列（全时段计数）
#   · ord_orders        user_id 集中在少量用户上 → 每人多页订单，验证 (user_id,id) 深分页
#   · mtg_reservations  约 15% 为「已过期日期 + 待确认/已确认」→ 给定时清理任务真实积压
#                       （远超单批 500，验证分批与自排空）
#   · mtg_reservations  企业成员 + 当月免费预约 → /free-hours 的企业维度聚合有数据
#   · mtg_bookings      仅给「未过期」预约建占用 → 时段冲突检测有真实对手
#   · sys_notifications 约 40% 未读 → 未读数与「仅未读」分页命中覆盖索引
#   · prd_products      约 8% 下架 / 2% 逻辑删除 → 布隆「只增不删」与公开列表过滤同时可验
#   · mkt_user_coupons  含已过期未使用券 → 惰性过期（expireUnused）有数据可处理
#
# 用法：
#   python3 db/seed_bulk.py                      # 默认量级（约 130 万行，视机器数十秒）
#   python3 db/seed_bulk.py --scale 0.2          # 快速小规模（约 26 万行）
#   python3 db/seed_bulk.py --scale 3            # 压测用大规模
#   python3 db/seed_bulk.py --batch 2000         # 加大单条 INSERT 的行数
#   python3 db/seed_bulk.py --purge-only         # 只清理叠加数据，不重新灌入
# 环境变量：DB_NAME / MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD
# 依赖：仅 mysql CLI（零第三方 Python 包，与 seed.py 一致）
# ============================================================================

import argparse
import os
import random
import shutil
import subprocess
import tempfile

# ---------------------------------------------------------------------------
# id 保留段：叠加数据一律使用 [BULK_ID_BASE, BULK_ID_BASE+BULK_ID_SPAN) 内的主键，
# 借此做到「精确清理自己、绝不误删核心种子」。
#
# 为什么选这个量级：核心种子用小 id（1..几百、SKU 10001+），
# db/seed_dev_user.py 用 990000000000000000+，
# 应用运行时 MyBatis-Plus 雪花 id 约 2.1e18。7e14 落在两者之间且远离双方，不会撞。
# ---------------------------------------------------------------------------
BULK_ID_BASE = 700_000_000_000_000
BULK_ID_SPAN = 1_000_000_000

# sys_notifications 是自增表（db/README.md「主键 ID 策略」）：显式写大 id 会把
# AUTO_INCREMENT 顶到 7e14，让之后应用新建的通知 id 量级突变。故该表不指定 id，
# 改用 title 前缀作为清理标记。
NOTIFY_TITLE_PREFIX = "[BULK]"

# 默认量级（--scale 1.0）。数字按「能让各优化可观测」的下限选取，兼顾灌入耗时。
DEFAULT_VOLUMES = {
    "users": 5_000,
    "enterprises": 200,
    "products": 20_000,
    "orders": 200_000,
    "order_items": 200_000,
    "meal_reservations": 50_000,
    "mtg_reservations": 120_000,
    "mtg_bookings": 40_000,
    "notifications": 300_000,
    "payments": 80_000,
    "recharge_records": 30_000,
    "balance_tx": 150_000,
    "user_coupons": 15_000,
}

# 会被叠加脚本写入的表（清理时按此顺序即可；本库无外键约束，顺序不影响）
BULK_TABLES = [
    "ord_order_items",
    "ord_orders",
    "ord_meal_reservation_items",
    "ord_meal_reservations",
    "ord_order_status_history",
    "mtg_bookings",
    "mtg_reservations",
    "mkt_user_coupons",
    "trd_balance_transactions",
    "trd_payments",
    "trd_recharge_records",
    "acct_accounts",
    "usr_enterprise_members",
    "usr_users",
    "usr_enterprises",
    "prd_skus",
    "prd_products",
]


def build_mysql_args(host, port, user, password):
    args = ["mysql", "-h", host, "-P", str(port), "-u", user]
    if password:
        args.append("-p" + password)
    return args


def run_sql(mysql_args, db, sql):
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as f:
        f.write("SET NAMES utf8mb4;\n")
        f.write(sql)
        path = f.name
    try:
        subprocess.run(mysql_args + [db], stdin=open(path, encoding="utf-8"), check=True)
    finally:
        os.unlink(path)


def q(v):
    """SQL 字符串字面量（单引号翻倍）"""
    return "'" + str(v).replace("'", "''") + "'"


def batched_insert(table, columns, rows, batch):
    """把行分批拼成多条多值 INSERT（避免逐行一条语句的开销）"""
    cols = ", ".join("`%s`" % c for c in columns)
    stmts, buf = [], []
    for r in rows:
        buf.append("(" + ",".join(r) + ")")
        if len(buf) >= batch:
            stmts.append("INSERT INTO `%s` (%s) VALUES %s;" % (table, cols, ",".join(buf)))
            buf = []
    if buf:
        stmts.append("INSERT INTO `%s` (%s) VALUES %s;" % (table, cols, ",".join(buf)))
    return stmts


class Gen:
    """按固定随机种子生成叠加数据，保证可重复执行结果一致"""

    def __init__(self, volumes, batch, seed=20260101):
        self.v = volumes
        self.batch = batch
        self.rnd = random.Random(seed)
        self.sql = []

    # ---- id 工具 ----------------------------------------------------------
    @staticmethod
    def uid(i):
        return BULK_ID_BASE + i

    def add(self, table, columns, rows):
        rows = list(rows)
        self.sql.extend(batched_insert(table, columns, rows, self.batch))
        return len(rows)

    # ---- 用户 / 企业 / 账户 ----------------------------------------------
    def users(self):
        n = self.v["users"]
        n_ent = self.v["enterprises"]
        rows = []
        for i in range(1, n + 1):
            # 约 40% 挂到批量企业下（企业会员），其余为个人用户（enterprise_id NULL）
            if i % 5 in (1, 2) and n_ent:
                ent = self.uid((i % n_ent) + 1)
                member_level = self.rnd.choice([1, 2, 3])
                is_admin = 1 if i % 5 == 1 and i % 15 == 0 else 0
            else:
                ent, member_level, is_admin = "NULL", 0, 0
            rows.append((
                str(self.uid(i)),
                q("bulk_openid_%06d" % i),
                q("压测用户%06d" % i),
                "3",                                  # user_type=3 路人
                str(ent) if ent != "NULL" else "NULL",
                str(member_level),
                str(is_admin),
                "1",                                  # status 正常
                "1" if i % 50 == 0 else "0",           # 约 2% 逻辑删除
                "NOW() - INTERVAL %d DAY" % self.rnd.randint(0, 400),
            ))
        return self.add("usr_users",
                        ["id", "openid", "nickname", "user_type", "enterprise_id",
                         "member_level", "is_enterprise_admin", "status", "is_deleted", "created_at"],
                        rows)

    def enterprises(self):
        n = self.v["enterprises"]
        rows = []
        for i in range(1, n + 1):
            audit = 1 if i % 10 else self.rnd.choice([0, 2])       # 九成审核通过
            level = self.rnd.choice([1, 2, 3]) if audit == 1 else 0
            expire = ("NOW() + INTERVAL %d DAY" % self.rnd.randint(30, 400)) if level else "NULL"
            rows.append((
                str(self.uid(i)),
                q("压测企业%04d有限公司" % i),
                q("BULK91440300MA5X%06d" % i),
                q("https://bulk/license%04d.png" % i),
                q("法人%04d" % i),
                q("https://bulk/id_front%04d.png" % i),
                q("https://bulk/id_back%04d.png" % i),
                q("联系人%04d" % i),
                q("139%08d" % i),
                str(self.rnd.choice([0, 1, 2])),                    # enterprise_type
                str(level),
                expire,
                str(audit),
                "1",
                "0",
                "NOW() - INTERVAL %d DAY" % self.rnd.randint(0, 400),
            ))
        return self.add("usr_enterprises",
                        ["id", "enterprise_name", "unified_social_credit_code", "business_license_url",
                         "legal_person_name", "legal_person_id_card_front", "legal_person_id_card_back",
                         "contact_name", "contact_phone", "enterprise_type", "member_level",
                         "member_expire_at", "audit_status", "status", "is_deleted", "created_at"],
                        rows)

    def accounts(self):
        rows = []
        for i in range(1, self.v["users"] + 1):
            rows.append((
                str(self.uid(i)), str(self.uid(i)),
                str(self.rnd.randint(0, 500_000)),      # available_balance
                str(self.rnd.choice([0, 0, 0, 10_000])),  # gift_balance
                "0", "1",
            ))
        return self.add("acct_accounts",
                        ["id", "user_id", "available_balance", "gift_balance", "frozen_balance", "status"],
                        rows)

    # ---- 商品 / SKU -------------------------------------------------------
    # 分类 → (类型, 名称词库, 价格区间[分])
    # 注意：product_type 语义上就对应分类（1-咖啡/2-正餐/3-加饭加菜/4-加汤），
    # 因此「类型跟随分类」是正确的，不是缺陷。
    PRODUCT_NAMES = {
        1: (1, ["美式", "拿铁", "卡布奇诺", "摩卡", "澳白", "冷萃", "浓缩", "焦糖玛奇朵"], (800, 3200)),
        2: (2, ["卤肉饭", "鸡腿饭", "牛肉面", "番茄意面", "咖喱鸡饭", "照烧鸡排饭", "三明治", "沙拉碗"], (1500, 4800)),
        3: (3, ["薯条", "鸡翅", "洋葱圈", "蔬菜沙拉", "溏心蛋", "玉米粒", "培根", "芝士条"], (500, 2200)),
        4: (4, ["例汤", "玉米浓汤", "番茄蛋汤", "紫菜蛋花汤", "罗宋汤", "菌菇汤"], (300, 1500)),
    }

    def products(self):
        n = self.v["products"]
        rows, skus = [], []
        for i in range(1, n + 1):
            pid = self.uid(i)
            # 约 8% 下架、2% 逻辑删除、其余上架 → 公开列表只应看到约 90%
            roll = i % 50
            if roll == 0:
                status, avail, deleted = 3, 0, 0        # 下架
            elif roll == 1:
                status, avail, deleted = 2, 1, 1        # 逻辑删除（行仍在，布隆不得判为不存在）
            else:
                status, avail, deleted = 2, 1, 0        # 上架

            # 属性一律用「独立随机」而非 i % k 推导。
            # 原实现用 i%4 / i%300 / i%1000 派生分类、价格、排序，模数互相关联，
            # 导致同一 sort_order 槽位恰好 4 个商品、且这些商品在各维度上完全同构
            # （前端表现为「4 张几乎一样的卡片挤在一起」，价格呈 10.20/20.20/30.20 锯齿）。
            cat = self.rnd.choices([1, 2, 3, 4], weights=[30, 30, 25, 15])[0]
            ptype, words, (lo, hi) = self.PRODUCT_NAMES[cat]
            price = self.rnd.randrange(lo, hi + 1, 10)
            # 名称：词库 + 序号后缀，既像真实菜单又不会重名
            name = "%s%03d" % (self.rnd.choice(words), i % 1000)
            # sort_order 恒为 0，与核心种子保持一致（db/seed.py 里商品的 sort_order 全是 0）。
            # 小程序查询接口按 (sort_order, id) 排序，全 0 时等价于「按 id 升序」= 入库顺序：
            # 核心商品在前、批量商品按序在后，顺序稳定可预期。
            # 不要改回 i % k 之类的推导：那会让同一排序槽位凑出固定个数的商品，
            # 且这些商品在其它维度上同构（前端表现为「好几张几乎一样的卡片挤在一起」）。
            sort_order = 0
            stock = self.rnd.choice([0, 20, 50, 100, 200, 500])
            rows.append((
                str(pid), str(cat), q(name), str(ptype),
                str(price), "NULL", "NULL", "NULL",
                str(avail), str(status), str(stock), str(sort_order), str(deleted),
                "NOW() - INTERVAL %d DAY" % self.rnd.randint(0, 400),
            ))
            # 注意：prd_skus 是少数没有 is_deleted 的表（见 db/README.md 逻辑删除范围）
            skus.append((
                str(self.uid(i)), q("BSKU%08d" % i), str(pid),
                str(price), str(stock), "1",
                "NOW() - INTERVAL %d DAY" % self.rnd.randint(0, 400),
            ))
        c1 = self.add("prd_products",
                      ["id", "category_id", "product_name", "product_type", "price", "description",
                       "image_url", "spec_options", "is_available", "product_status", "stock",
                       "sort_order", "is_deleted", "created_at"],
                      rows)
        c2 = self.add("prd_skus",
                      ["id", "sku_code", "product_id", "price", "stock", "status", "created_at"],
                      skus)
        return c1 + c2

    # ---- 订单 / 明细 ------------------------------------------------------
    def orders(self):
        n = self.v["orders"]
        n_users = self.v["users"]
        n_products = self.v["products"]
        rows, items = [], []
        # 状态分布：0 待支付 / 1 待取餐 / 2 制作中 / 3 已完成 / 4 已取消 / 5 已退款
        status_pool = [3] * 45 + [4] * 15 + [5] * 8 + [1] * 12 + [2] * 10 + [0] * 10
        # 1% 的「重度用户」承接约 1/4 订单 → 让 (user_id,id) 索引下的深分页
        # （大量 OFFSET）有真实数据可测，而不是每个用户只有几十单
        n_heavy = max(1, n_users // 100)
        for i in range(1, n + 1):
            oid = self.uid(i)
            user_idx = (i % n_heavy) + 1 if i % 4 == 0 else (i % n_users) + 1
            # 约 1.5% 落在「今天」，其余铺满近 365 天 → 让今日聚合与历史聚合都有量
            if i % 67 == 0:
                created = "NOW() - INTERVAL %d MINUTE" % self.rnd.randint(0, 900)
            else:
                created = "NOW() - INTERVAL %d DAY" % (i % 365)
            status = status_pool[i % len(status_pool)]
            total = (i % 20 + 1) * 1500
            rows.append((
                str(oid), q("BLK%012d" % i), str(self.uid(user_idx)), "NULL",
                "1", str(status), str(total), str(total), q("%06d" % (i % 1_000_000)),
                created,
            ))
            if i <= self.v["order_items"]:
                pid = self.uid((i % n_products) + 1)
                items.append((
                    str(self.uid(i)), str(oid), str(pid), q("压测商品%06d" % ((i % n_products) + 1)),
                    str(total), "1", str(total), str(total), str(total), created,
                ))
        c1 = self.add("ord_orders",
                      ["id", "order_no", "user_id", "enterprise_id", "order_type", "order_status",
                       "total_amount", "payable_amount", "pickup_code", "created_at"],
                      rows)
        c2 = self.add("ord_order_items",
                      ["id", "order_id", "product_id", "product_name", "product_price", "quantity",
                       "subtotal", "discounted_price", "discounted_subtotal", "created_at"],
                      items)
        return c1 + c2

    # ---- 餐预约 -----------------------------------------------------------
    def meal_reservations(self):
        n = self.v["meal_reservations"]
        n_users = self.v["users"]
        n_products = self.v["products"]
        rows = []
        status_pool = [3] * 40 + [1] * 25 + [2] * 15 + [4] * 12 + [0] * 8
        for i in range(1, n + 1):
            pid = self.uid((i % n_products) + 1)
            amount = (i % 12 + 1) * 1200
            # 近 120 天内来回铺，其中 1/5 落在今天 → 与订单一起压今日聚合
            day = 0 if i % 5 == 0 else (i % 120)
            rows.append((
                str(self.uid(i)), q("BLMR%012d" % i), str(self.uid((i % n_users) + 1)), "NULL",
                str(pid), q("压测套餐%06d" % ((i % n_products) + 1)),
                "CURDATE() - INTERVAL %d DAY" % day,
                "CURDATE() - INTERVAL %d DAY" % day,
                q(self.rnd.choice(["11:00-12:00", "12:00-13:00", "17:30-18:30"])),
                "1", str(amount), str(amount), str(status_pool[i % len(status_pool)]),
                "NOW() - INTERVAL %d DAY" % day,
            ))
        return self.add("ord_meal_reservations",
                        ["id", "reservation_no", "user_id", "enterprise_id", "product_id", "product_name",
                         "menu_date", "reservation_date", "reservation_time_slot", "quantity",
                         "total_amount", "payable_amount", "status", "created_at"],
                        rows)

    # ---- 会议预约 / 占用 ---------------------------------------------------
    def mtg_reservations(self):
        n = self.v["mtg_reservations"]
        n_users = self.v["users"]
        n_rooms = 8
        res_rows, booking_rows = [], []
        booking_id = 0
        for i in range(1, n + 1):
            rid = self.uid(i)
            room = (i % n_rooms) + 1
            user = self.uid((i % n_users) + 1)
            enterprise = "0"
            if i % 5 in (1, 2):                       # 企业成员：走企业维度免费时长
                enterprise = str(self.uid((i % max(1, self.v["enterprises"])) + 1))
            # 分布刻意模拟真实台账，避免「过去日期 ⟺ 仍未处理」这种不真实的相关性：
            #   · 约 60% 为过去日期，其中绝大多数早已终态（已完成/已取消/已过期），
            #     只有一小撮（约 1/8）仍停在 0/1 —— 这才是定时清理任务的真实积压；
            #   · 其余为未来日期，处于正常在约状态。
            # 这样 `reservation_date < CURDATE()` 单独就不再有选择性（匹配约六成），
            # 必须靠 (status, reservation_date) 组合索引才能高效取批 ——
            # 与 docs 评估 §2.8 实测的查询形态一致。
            if i % 5 < 3:                             # 60% 过去
                day_expr = "CURDATE() - INTERVAL %d DAY" % (1 + (i % 120))
                if i % 8 == 0:                        # 过去件里约 1/8 仍待处理 → 清理积压
                    status = 0 if i % 16 == 0 else 1
                    is_free = 1
                    fee = 0
                else:                                 # 其余早已终态
                    status = [3, 4, 5][i % 3]
                    is_free = 0
                    fee = 0 if status == 5 else 8000
                make_booking = False                  # 过去件不占用（避免脏占用）
            else:                                     # 40% 未来，正常在约
                day_expr = "CURDATE() + INTERVAL %d DAY" % (i % 30)
                status = [1, 2, 3][i % 3]
                is_free = 0 if i % 3 else 1
                fee = 0 if is_free else 8000
                make_booking = status in (1, 2)
            res_rows.append((
                str(rid), q("BLMT%012d" % i), str(room), str(user), enterprise,
                day_expr, q("09:00:00"), q("11:00:00"), "2.0",
                q("压测会议%06d" % i), str(status), str(is_free), str(fee),
                "8000", "0.0",
                "NOW() - INTERVAL %d DAY" % (i % 200),
            ))
            if make_booking and booking_id < self.v["mtg_bookings"]:
                booking_id += 1
                booking_rows.append((
                    str(self.uid(booking_id)), str(room), str(rid),
                    "TIMESTAMP(%s, '09:00:00')" % day_expr,
                    "TIMESTAMP(%s, '11:00:00')" % day_expr,
                    "0", "NOW() - INTERVAL %d DAY" % (i % 200),
                ))
        c1 = self.add("mtg_reservations",
                      ["id", "reservation_no", "room_id", "user_id", "enterprise_id", "reservation_date",
                       "start_time", "end_time", "duration_hours", "meeting_topic", "status",
                       "is_free", "fee_amount", "overtime_unit_price", "free_hours_deducted", "created_at"],
                      res_rows)
        c2 = self.add("mtg_bookings",
                      ["id", "room_id", "reservation_id", "start_at", "end_at", "status", "created_at"],
                      booking_rows)
        return c1 + c2

    # ---- 通知（自增表：不指定 id，用 title 前缀清理）-----------------------
    def notifications(self):
        n = self.v["notifications"]
        n_users = self.v["users"]
        rows = []
        for i in range(1, n + 1):
            unread = 1 if i % 5 in (1, 2) else 0        # 约 40% 未读
            rows.append((
                str(self.uid((i % n_users) + 1)),
                str((i % 7) + 1),
                q("%s 压测通知%08d" % (NOTIFY_TITLE_PREFIX, i)),
                q("这是用于压测未读数与分页的通知内容 #%d" % i),
                "1",
                ("NOW() - INTERVAL %d MINUTE" % self.rnd.randint(0, 600)),
                str(unread),
                "NOW() - INTERVAL %d DAY" % (i % 180),
            ))
        return self.add("sys_notifications",
                        ["user_id", "notification_type", "title", "content", "send_status",
                         "send_time", "is_read", "created_at"],
                        rows)

    # ---- 资金流水 / 支付单 / 充值 / 用户券 --------------------------------
    def money(self):
        n_users = self.v["users"]
        tx, pays, recharges = [], [], []
        for i in range(1, self.v["balance_tx"] + 1):
            amount = (i % 30 + 1) * 500
            before = self.rnd.randint(0, 400_000)
            tx.append((
                str(self.uid(i)), str(self.uid((i % n_users) + 1)),
                str(self.rnd.choice([1, 2, 3, 4])), str(amount),
                str(before), str(before + amount), "NULL",
                "NOW() - INTERVAL %d DAY" % (i % 365),
            ))
        biz_pool = [1] * 6 + [5] * 2 + [2] * 2
        for i in range(1, self.v["payments"] + 1):
            oid = self.uid((i % self.v["orders"]) + 1)
            pays.append((
                str(self.uid(i)), q("BLP%012d" % i), str(self.uid((i % n_users) + 1)),
                str(biz_pool[i % len(biz_pool)]), str(oid),
                str((i % 20 + 1) * 1500), "1", "1",
                "1", q("BLO%014d" % i), q("BLT%014d" % i),
                "NOW() - INTERVAL %d DAY" % (i % 365),
            ))
        for i in range(1, self.v["recharge_records"] + 1):
            base = [10000, 20000, 50000, 100000, 200000, 300000][i % 6]
            recharges.append((
                str(self.uid(i)), str(self.uid((i % n_users) + 1)), str((i % 6) + 1),
                str(base), str(base // 10), str(base + base // 10),
                "1", q("BLR%014d" % i),
                "NOW() - INTERVAL %d DAY" % (i % 365),
            ))
        coupons = []
        for i in range(1, self.v["user_coupons"] + 1):
            # 约 1/4 为「已过期且未使用」→ expirinUnused 惰性过期有数据可做
            expired = i % 4 == 0
            expire_at = ("NOW() - INTERVAL %d DAY" % (1 + i % 30)) if expired \
                else ("NOW() + INTERVAL %d DAY" % (1 + i % 60))
            coupons.append((
                str(self.uid(i)), str((i % 3) + 1), str(self.uid((i % n_users) + 1)),
                q("压测券%s" % ("(已过期)" if expired else "")), "1", "500", "0",
                str(0 if expired else self.rnd.choice([0, 1])),
                expire_at,
                "NOW() - INTERVAL %d DAY" % (i % 200),
            ))

        c = 0
        c += self.add("trd_balance_transactions",
                      ["id", "user_id", "transaction_type", "amount", "balance_before", "balance_after",
                       "related_order_id", "created_at"], tx)
        c += self.add("trd_payments",
                      ["id", "payment_no", "user_id", "biz_type", "biz_id", "amount", "payment_method",
                       "payment_channel", "status", "out_trade_no", "transaction_id", "created_at"], pays)
        c += self.add("trd_recharge_records",
                      ["id", "user_id", "tier_id", "recharge_amount", "bonus_amount", "total_amount",
                       "payment_status", "out_trade_no", "created_at"], recharges)
        c += self.add("mkt_user_coupons",
                      ["id", "coupon_id", "user_id", "coupon_name", "coupon_type", "discount_amount",
                       "threshold_amount", "status", "expire_at", "created_at"], coupons)
        return c

    # ---- 企业成员关系 -----------------------------------------------------
    def members(self):
        n_users = self.v["users"]
        n_ent = self.v["enterprises"]
        rows, mid = [], 0
        for i in range(1, n_users + 1):
            if i % 5 not in (1, 2) or not n_ent:
                continue
            mid += 1
            rows.append((
                str(self.uid(mid)), str(self.uid((i % n_ent) + 1)), str(self.uid(i)),
                "1" if i % 15 == 0 else "0",                 # role：少量企业管理员
                "1" if i % 20 else "0",                      # invite_status：少量待接受
                "NOW() - INTERVAL %d DAY" % self.rnd.randint(0, 200),
                "NOW() - INTERVAL %d DAY" % self.rnd.randint(0, 200) if i % 20 else "NULL",
                "0",
            ))
        return self.add("usr_enterprise_members",
                        ["id", "enterprise_id", "user_id", "role", "invite_status",
                         "invited_at", "accepted_at", "is_deleted"], rows)


def flush_app_cache():
    """清空应用缓存命名空间（cache:*），让正在运行的实例立刻看到新的种子数据。

    必要性：本脚本直接写 MySQL，不经过应用，因此不会触发商品列表的「缓存代」推进；
    若不清缓存，运行中的实例会继续返回旧数据（分页键 TTL 10 分钟）。
    清掉 cache:* 会连「缓存代」计数器一起删掉 —— 而缓存代缺失时会用当前时间戳重新初始化，
    新代一定大于旧代，于是连各实例进程内 L1 里的旧键也一并失效。

    redis-cli 不可用时只提示，不影响灌数据结果（与 reset_db.sh 的处理一致）。
    """
    if not shutil.which("redis-cli"):
        print("==> 提示：未找到 redis-cli，若应用正在运行请重启或手动清理 cache:* 键")
        return
    host = os.environ.get("REDIS_HOST", "127.0.0.1")
    port = os.environ.get("REDIS_PORT", "6379")
    base = ["redis-cli", "-h", host, "-p", port]
    if os.environ.get("REDIS_PASSWORD"):
        base += ["-a", os.environ["REDIS_PASSWORD"], "--no-auth-warning"]
    try:
        scan = subprocess.run(base + ["--scan", "--pattern", "cache:*"],
                              capture_output=True, text=True, timeout=10)
        keys = [k for k in scan.stdout.split() if k]
        if keys:
            subprocess.run(base + ["DEL"] + keys, capture_output=True, timeout=10)
            print("==> 已清理应用缓存（cache:*，共 %d 个键），运行中的实例将重新加载" % len(keys))
    except Exception as e:  # noqa: BLE001 - 缓存清理失败不应让灌数据失败
        print("==> 提示：清理应用缓存失败（%s），若应用正在运行请手动清理 cache:*" % e)


def build_purge_sql():
    """只清理叠加数据：显式 id 段精确删除 + 通知表按 title 标记删除"""
    stmts = ["SET FOREIGN_KEY_CHECKS = 0;"]
    for t in BULK_TABLES:
        stmts.append(
            "DELETE FROM `%s` WHERE `id` >= %d AND `id` < %d;" % (t, BULK_ID_BASE, BULK_ID_BASE + BULK_ID_SPAN)
        )
    stmts.append("DELETE FROM `sys_notifications` WHERE `title` LIKE '%s%%';" % NOTIFY_TITLE_PREFIX)
    stmts.append("SET FOREIGN_KEY_CHECKS = 1;")
    return stmts


def main():
    parser = argparse.ArgumentParser(
        description="开发用规模种子叠加（dev-only；跑集成测试前请先 make db-reset）")
    parser.add_argument("--host", default=os.environ.get("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("MYSQL_PORT", "3306")))
    parser.add_argument("--user", default=os.environ.get("MYSQL_USER", "root"))
    parser.add_argument("--password", default=os.environ.get("MYSQL_PASSWORD", "123456"))
    parser.add_argument("--db", default=os.environ.get("DB_NAME", "lease_db"))
    parser.add_argument("--scale", type=float, default=1.0, help="按默认量级缩放（默认 1.0；0.2 为快速小规模）")
    parser.add_argument("--batch", type=int, default=1000, help="单条 INSERT 的行数（默认 1000）")
    parser.add_argument("--seed", type=int, default=20260101, help="随机种子（保证可重复执行结果一致）")
    parser.add_argument("--purge-only", action="store_true", help="只清理叠加数据，不重新灌入")
    args = parser.parse_args()

    if args.scale <= 0:
        parser.error("--scale 必须为正数")

    volumes = {k: max(1, int(v * args.scale)) for k, v in DEFAULT_VOLUMES.items()}
    # 明细类表不能超过其主表
    volumes["order_items"] = min(volumes["order_items"], volumes["orders"])
    volumes["mtg_bookings"] = min(volumes["mtg_bookings"], volumes["mtg_reservations"])

    mysql_args = build_mysql_args(args.host, args.port, args.user, args.password)

    print("==> 清理上次叠加的数据（id 段 / 通知 title 标记）...")
    run_sql(mysql_args, args.db, "\n".join(build_purge_sql()))

    if args.purge_only:
        flush_app_cache()   # 清理同样改变了数据，运行中的实例也必须失效
        print("==> --purge-only：已清理，未灌入新数据")
        return

    print("==> 生成叠加数据（scale=%.2f）： %s" % (
        args.scale, ", ".join("%s=%d" % (k, v) for k, v in sorted(volumes.items()))))
    gen = Gen(volumes, args.batch, args.seed)
    counts = {
        "usr_users": gen.users(),
        "usr_enterprises": gen.enterprises(),
        "usr_enterprise_members": gen.members(),
        "acct_accounts": gen.accounts(),
        "prd_products+prd_skus": gen.products(),
        "ord_orders+items": gen.orders(),
        "ord_meal_reservations": gen.meal_reservations(),
        "mtg_reservations+bookings": gen.mtg_reservations(),
        "sys_notifications": gen.notifications(),
        "money(tx/pay/recharge/coupon)": gen.money(),
    }

    # 大表批量导入：关 autocommit 提吞吐（唯一性由构造保证，不禁用 unique_checks）
    body = "SET autocommit = 0;\n" + "\n".join(gen.sql) + "\nCOMMIT;\n"
    print("==> 灌入 %s（%d 条 INSERT 语句）..." % (args.db, len(gen.sql)))
    run_sql(mysql_args, args.db, body)

    flush_app_cache()

    total = sum(counts.values())
    print("==> 完成，共叠加约 %d 行：" % total)
    for k, v in counts.items():
        print("    %-32s %d" % (k, v))
    print("==> 提示：跑集成测试前请 make db-reset 回到纯净种子；"
          "只清理叠加数据用 python3 db/seed_bulk.py --purge-only")


if __name__ == "__main__":
    main()
