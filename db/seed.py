#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# ============================================================================
# 开发种子数据生成脚本（与建表分离）
# ----------------------------------------------------------------------------
# 职责：db/*.sql 只负责建表（DDL），本脚本负责灌入开发用种子数据。
#       生产环境不要执行本脚本（上生产只跑建表，见 Makefile db-init / reset_db.sh）。
#
# 幂等：先清空再插入（自增表重置 AUTO_INCREMENT，保证 id 稳定）。
# 兼容性：核心种子（商品 id 1-8 / 2026-08-30 菜单 11 条 / 充值档位 tierId=2=50000 /
#        会议室 A/B/C / 会员等级 VIP 4h / admin 账号）与集成测试断言一致，
#        修改时必须保持；扩充数据（更多商品/菜单/会议室/档位/演示企业）仅开发使用。
#
# 用法：python3 db/seed.py                       （默认本机 root/123456/lease_db）
#       python3 db/seed.py --db xxx --password yyy
# 环境变量：DB_NAME / MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD
# 依赖：仅 mysql CLI（零第三方 Python 包）
# ============================================================================
import argparse
import os
import subprocess
import sys
import tempfile
import itertools
import json
from datetime import date, timedelta

ADMIN_BCRYPT = "$2a$10$ZUXdPnydoz4kKJQYT7aRw.rT9dhuPOgr6GySeCmwolTGl1r1LvdMO"  # admin / 123456

# 核心商品（id 1-8 固定，集成测试断言依赖，勿动）
CORE_PRODUCTS = [
    (1, 1, "美式", 1, 1200, None, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
    (2, 1, "拿铁", 1, 1500, None, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
    (3, 1, "奶茶", 1, 1800, None, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
    (4, 2, "3荤1素套餐", 2, 2000, "每日更新菜单，3种荤菜+1种素菜", None),
    (5, 2, "4荤1素套餐", 2, 2500, "每日更新菜单，4种荤菜+1种素菜", None),
    (6, 3, "加饭", 3, 500, "额外加一份米饭", None),
    (7, 3, "加菜", 3, 500, "额外加一份菜品", None),
    (8, 4, "加汤", 4, 800, "额外加一份汤", None),
]

# 扩充商品（id 9+ 连续，与核心 1-8 衔接；开发演示用）
EXTRA_PRODUCTS = [
    # 咖啡
    (9, 1, "卡布奇诺", 1, 2200, None, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
    (10, 1, "摩卡", 1, 2500, None, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
    (11, 1, "焦糖玛奇朵", 1, 2800, None, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
    (12, 1, "澳白", 1, 2600, None, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖"]}'),
    (13, 1, "燕麦拿铁", 1, 2300, None, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖"]}'),
    (14, 1, "生椰拿铁", 1, 2400, None, '{"cup_size":["大杯","中杯"],"temperature":["冰"],"sugar":["无糖","少糖"]}'),
    (15, 1, "冷萃", 1, 2800, None, '{"cup_size":["大杯","中杯"],"ice":["多冰","少冰"]}'),
    (16, 1, "气泡美式", 1, 2500, None, '{"cup_size":["大杯","中杯"],"temperature":["冰"]}'),
    (17, 1, "榛果拿铁", 1, 2000, None, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
    (18, 1, "香草拿铁", 1, 2000, None, '{"cup_size":["大杯","中杯"],"temperature":["热","冰"],"sugar":["无糖","少糖","正常"]}'),
    (19, 1, "冰美式", 1, 1300, None, '{"cup_size":["大杯","中杯"],"ice":["多冰","少冰"]}'),
    (20, 1, "冰拿铁", 1, 1600, None, '{"cup_size":["大杯","中杯"],"ice":["多冰","少冰"],"sugar":["无糖","少糖","正常"]}'),
    # 正餐套餐
    (21, 2, "牛肉面套餐", 2, 2200, "每日更新菜单，牛肉面+配菜", None),
    (22, 2, "鸡腿饭套餐", 2, 2000, "每日更新菜单，鸡腿饭+配菜", None),
    (23, 2, "2荤2素套餐", 2, 1800, "每日更新菜单，2种荤菜+2种素菜", None),
    (24, 2, "5荤1素套餐", 2, 3000, "每日更新菜单，5种荤菜+1种素菜", None),
    # 加餐 / 加汤
    (25, 3, "加蛋", 3, 200, "额外加一个蛋", None),
    (26, 3, "加卤蛋", 3, 300, "额外加一个卤蛋", None),
    (27, 4, "玉米排骨汤", 4, 1000, "额外加一份汤", None),
]

# 每日菜单菜谱：套餐 product_id -> [(菜品, dish_type), ...]（扩充菜单按天循环取）
MENU_RECIPES = {
    4: [("红烧肉", 1), ("宫保鸡丁", 1), ("鱼香肉丝", 1), ("清炒时蔬", 2)],
    5: [("红烧排骨", 1), ("辣子鸡", 1), ("水煮牛肉", 1), ("梅菜扣肉", 1), ("蒜蓉西兰花", 2)],
    120: [("红烧牛肉", 1), ("白灼青菜", 2), ("卤蛋", 3), ("牛肉面汤", 3)],
    121: [("照烧鸡腿", 1), ("蒜蓉时蔬", 2), ("紫菜蛋花汤", 3), ("米饭", 4)],
    122: [("回锅肉", 1), ("木须肉", 1), ("清炒西兰花", 2), ("酸辣土豆丝", 2)],
    123: [("红烧排骨", 1), ("辣子鸡", 1), ("水煮牛肉", 1), ("梅菜扣肉", 1), ("清蒸鲈鱼", 1), ("白灼菜心", 2)],
}

# 会议室：核心 A/B/C + 扩充（超出费用统一按「会员等级默认价 + 会议室覆盖价」解析，
# mtg_rooms 不再持有 hourly_fee；等级默认价见 MEMBER_LEVELS.meeting_overtime_fee=8000）
ROOMS = [
    ("会议室A", 10, "投影仪、白板、音响", "沙龙、培训、路演、商务洽谈"),
    ("会议室B", 10, "投影仪、白板、音响", "沙龙、培训、路演、商务洽谈"),
    ("会议室C", 10, "投影仪、白板、音响", "沙龙、培训、路演、商务洽谈"),
    ("小会议室", 4, "白板", "一对一洽谈、小规模讨论"),
    ("标准会议室", 8, "投影仪、白板", "部门例会、客户洽谈"),
    ("大会议室", 20, "投影仪、白板、音响、视频会议", "全员会议、培训"),
    ("路演厅", 50, "LED屏、音响、演讲台", "路演、发布会、大型培训"),
    ("贵宾洽谈室", 6, "白板、茶歇服务", "商务谈判、贵宾接待"),
]

# 会议室等级定价覆盖（Override，room_id 对应上面插入顺序 1..8）：
# 未配置的（会议室 × 等级）回落 usr_member_levels.meeting_overtime_fee（8000）。
# 演示：路演厅(7)/贵宾洽谈室(8) 对所有等级额外加收；小会议室(4) VIP 优惠价。
ROOM_LEVEL_PRICES = [
    (4, "VIP", 4000),
    (7, "BASIC", 12000), (7, "VIP", 12000), (7, "SVIP", 12000),
    (8, "BASIC", 15000), (8, "VIP", 15000), (8, "SVIP", 15000),
]

# 充值档位：核心 4 档（tierId=2 必须是 50000/6000，测试依赖）+ 扩充
# 注意：扩充档位金额须避开 10000/20000/50000/100000/200000（集成测试会创建 10000 档并复用 20000 档做重复用例）
RECHARGE_TIERS = [
    (20000, 2000, 22000, 0.91, 1),
    (50000, 6000, 56000, 0.89, 2),
    (100000, 15000, 115000, 0.87, 3),
    (200000, 40000, 240000, 0.83, 4),
    (30000, 3000, 33000, 0.90, 5),
    (800000, 200000, 1000000, 0.78, 6),
]

# 会员等级（VIP 4h/月为测试依赖）
MEMBER_LEVELS = [
    ("BASIC", "基础版", 0, 0.95, 0, 0, 0, 8000, "老板本人9折/95折"),
    ("VIP", "VIP版", 500000, 0.90, 4, 2, 1, 8000, "全公司员工8折/9折"),
    ("SVIP", "SVIP版", 1200000, 0.85, 8, 1, 2, 8000, "全公司员工7折/85折"),
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


def esc(v):
    """SQL 字符串转义（单引号翻倍）"""
    return str(v).replace("'", "''")


def build_product_skus(products):
    """由商品规格生成 规格组/规格值/SKU（1.3）：
    - 有规格（spec_options JSON：{规格组: [规格值...]}）→ 规格组/值入库，SKU = 各规格值笛卡尔积，
      价格沿用商品价；spec_value_ids / spec_snapshot 存 JSON；
    - 无规格 → 生成 1 条默认 SKU（规格为空）。
    SKU id 雪花位（显式 10001+），sku_code = SKU{商品id:04d}{序号:02d}。
    """
    group_rows, value_rows, sku_rows = [], [], []
    gid = 1  # prd_spec_groups 自增（种子重置后从 1 开始）
    vid = 1  # prd_spec_values 自增
    sku_id = 10001
    value_names = {}  # vid -> value_name（构造规格快照用）
    for pid, _cid, _name, _ptype, price, _desc, spec in products:
        groups = None
        if spec:
            try:
                groups = json.loads(spec)
            except Exception:
                groups = None
        if groups:
            group_defs = []  # [(group_name, [value_id...])]
            for gidx, (gname, values) in enumerate(groups.items(), start=1):
                group_rows.append((pid, gname, gidx))
                vids = []
                for vidx, vname in enumerate(values, start=1):
                    value_rows.append((gid, vname, vidx))
                    value_names[vid] = vname
                    vids.append(vid)
                    vid += 1
                group_defs.append((gname, vids))
                gid += 1
            combos = itertools.product(*[vids for _, vids in group_defs])
            for idx, combo in enumerate(combos, start=1):
                snapshot = {gname: value_names[v] for (gname, _), v in zip(group_defs, combo)}
                sku_rows.append((
                    sku_id, f"SKU{pid:04d}{idx:02d}", pid,
                    json.dumps(list(combo)), json.dumps(snapshot, ensure_ascii=False), price))
                sku_id += 1
        else:
            sku_rows.append((sku_id, f"SKU{pid:04d}00", pid, "NULL", "NULL", price))
            sku_id += 1
    return group_rows, value_rows, sku_rows


def build_sql():
    sql = []
    # ---------- 清空全部业务表（幂等；含测试/手动操作产生的残留，保证种子后为全新一致状态） ----------
    tables = [
        # 系统域
        "sys_operation_logs", "sys_notifications",
        # 订单域
        "ord_meal_reservation_items", "ord_meal_reservations", "ord_order_items", "ord_orders",
        # 商品域
        "prd_skus", "prd_spec_values", "prd_spec_groups", "prd_daily_menus",
        "prd_products", "prd_categories",
        # 会议室域
        "mtg_reservations", "mtg_room_level_prices", "mtg_rooms",
        # 交易域
        "trd_balance_transactions", "trd_recharge_records", "trd_recharge_tiers",
        # 用户域
        "usr_member_purchases", "usr_enterprise_members", "usr_member_levels",
        "usr_enterprises", "usr_users", "usr_admins",
    ]
    for t in tables:
        sql.append(f"DELETE FROM `{t}`;")
    sql.append("ALTER TABLE `prd_categories` AUTO_INCREMENT = 1;")
    sql.append("ALTER TABLE `prd_spec_groups` AUTO_INCREMENT = 1;")
    sql.append("ALTER TABLE `prd_spec_values` AUTO_INCREMENT = 1;")
    sql.append("ALTER TABLE `mtg_rooms` AUTO_INCREMENT = 1;")
    sql.append("ALTER TABLE `mtg_room_level_prices` AUTO_INCREMENT = 1;")
    sql.append("ALTER TABLE `trd_recharge_tiers` AUTO_INCREMENT = 1;")
    sql.append("ALTER TABLE `usr_member_levels` AUTO_INCREMENT = 1;")
    sql.append("ALTER TABLE `usr_admins` AUTO_INCREMENT = 1;")

    # ---------- 商品分类（4 分类，测试断言） ----------
    sql.append("INSERT INTO `prd_categories` (`category_name`, `category_type`, `sort_order`) VALUES "
               "('咖啡', 1, 1), ('正餐', 2, 2), ('加餐/加菜', 2, 3), ('加汤', 2, 4);")

    # ---------- 商品（核心 1-8 + 扩充 9-27，id 连续） ----------
    products = CORE_PRODUCTS + EXTRA_PRODUCTS
    rows = ", ".join(
        f"({pid}, {cid}, '{esc(name)}', {ptype}, {price}, "
        + (f"'{esc(desc)}'" if desc else "NULL") + ", "
        + (f"'{esc(spec)}'" if spec else "NULL") + ")"
        for pid, cid, name, ptype, price, desc, spec in products
    )
    sql.append("INSERT INTO `prd_products` (`id`, `category_id`, `product_name`, `product_type`, "
               f"`price`, `description`, `spec_options`) VALUES {rows};")

    # ---------- 商品规格组 / 规格值 / SKU（1.3：规格组合 → SKU；无规格商品生成默认 SKU） ----------
    group_rows, value_rows, sku_rows = build_product_skus(products)
    if group_rows:
        rows = ", ".join(f"({pid}, '{esc(gname)}', {so})" for pid, gname, so in group_rows)
        sql.append("INSERT INTO `prd_spec_groups` (`product_id`, `group_name`, `sort_order`) "
                   f"VALUES {rows};")
    if value_rows:
        rows = ", ".join(f"({gid}, '{esc(vname)}', {so})" for gid, vname, so in value_rows)
        sql.append("INSERT INTO `prd_spec_values` (`group_id`, `value_name`, `sort_order`) "
                   f"VALUES {rows};")
    if sku_rows:
        def sku_json(v):
            # JSON 列值：'NULL' 保持 NULL，其余加单引号（MySQL JSON 字面量需引号）
            return "NULL" if v == "NULL" else "'" + esc(v) + "'"
        rows = ", ".join(
            "({sid}, '{code}', {pid}, {sv}, {ss}, {price})".format(
                sid=sid, code=code, pid=pid, sv=sku_json(svids),
                ss=sku_json(ssnap), price=price)
            for sid, code, pid, svids, ssnap, price in sku_rows)
        sql.append("INSERT INTO `prd_skus` (`id`, `sku_code`, `product_id`, `spec_value_ids`, "
                   f"`spec_snapshot`, `price`) VALUES {rows};")

    # ---------- 每日菜单：核心 2026-08-30（11 条，测试断言）+ 扩充未来 3 天 ----------
    menu_rows = []
    core_menu = [
        (1, "2026-08-30", 4, "红烧肉", 1, 1), (2, "2026-08-30", 4, "宫保鸡丁", 1, 2),
        (3, "2026-08-30", 4, "鱼香肉丝", 1, 3), (4, "2026-08-30", 4, "清炒时蔬", 2, 4),
        (5, "2026-08-30", 5, "红烧排骨", 1, 1), (6, "2026-08-30", 5, "辣子鸡", 1, 2),
        (7, "2026-08-30", 5, "水煮牛肉", 1, 3), (8, "2026-08-30", 5, "梅菜扣肉", 1, 4),
        (9, "2026-08-30", 5, "蒜蓉西兰花", 2, 5), (10, "2026-08-30", 8, "紫菜蛋花汤", 3, 1),
        (11, "2026-08-30", 8, "番茄蛋汤", 3, 2),
    ]
    menu_rows.extend(core_menu)
    mid = 12  # 核心 1-11 之后连续编号
    for offset in (1, 2, 3):
        day = (date.today() + timedelta(days=offset)).isoformat()
        for pid in sorted(MENU_RECIPES):
            for dish, dtype in MENU_RECIPES[pid]:
                menu_rows.append((mid, day, pid, dish, dtype, len(menu_rows) % 10 + 1))
                mid += 1
    rows = ", ".join(
        f"({i}, '{d}', {pid}, '{esc(name)}', {dt}, {so})"
        for i, d, pid, name, dt, so in menu_rows
    )
    sql.append("INSERT INTO `prd_daily_menus` (`id`, `menu_date`, `product_id`, `dish_name`, "
               f"`dish_type`, `sort_order`) VALUES {rows};")

    # ---------- 会议室（3 核心 + 5 扩充） ----------
    rows = ", ".join(f"('{esc(name)}', {cap}, '{esc(equip)}', '{esc(scene)}')"
                     for name, cap, equip, scene in ROOMS)
    sql.append("INSERT INTO `mtg_rooms` (`room_name`, `capacity`, `equipment`, `suitable_scenes`) "
               f"VALUES {rows};")

    # ---------- 会议室等级定价覆盖（无覆盖时回落等级默认价） ----------
    rows = ", ".join(f"({rid}, '{code}', {fee})" for rid, code, fee in ROOM_LEVEL_PRICES)
    sql.append("INSERT INTO `mtg_room_level_prices` (`room_id`, `level_code`, `overtime_fee`) "
               f"VALUES {rows};")

    # ---------- 充值档位（6 档） ----------
    rows = ", ".join(f"({r}, {b}, {a}, {d}, {s})" for r, b, a, d, s in RECHARGE_TIERS)
    sql.append("INSERT INTO `trd_recharge_tiers` (`recharge_amount`, `bonus_amount`, `actual_amount`, "
               f"`equivalent_discount`, `sort_order`) VALUES {rows};")

    # ---------- 会员等级（3 级） ----------
    rows = ", ".join(
        f"('{code}', '{esc(name)}', {price}, {rate}, {hours}, {adv}, {prio}, {fee}, '{esc(desc)}')"
        for code, name, price, rate, hours, adv, prio, fee, desc in MEMBER_LEVELS
    )
    sql.append("INSERT INTO `usr_member_levels` (`level_code`, `level_name`, `price`, `discount_rate`, "
               "`monthly_meeting_hours`, `meeting_booking_advance_days`, `meeting_priority`, "
               f"`meeting_overtime_fee`, `description`) VALUES {rows};")

    # ---------- 后台管理员（admin / 123456，与迁移 V2 一致） ----------
    sql.append(f"INSERT INTO `usr_admins` (`username`, `password_hash`, `name`, `role`, `status`) "
               f"VALUES ('admin', '{ADMIN_BCRYPT}', '超级管理员', 1, 1);")

    # ---------- 演示用户 + 企业（仅开发，openid 避开测试用的 mock_dev_user） ----------
    sql.append("INSERT INTO `usr_users` (`id`, `openid`, `nickname`, `user_type`, `enterprise_id`, "
               "`member_level`, `is_enterprise_admin`, `balance`, `gift_balance`, `status`) VALUES "
               "(1, 'mock_demo_admin', '演示企业主', 2, 1, 2, 1, 200000, 50000, 1), "
               "(2, 'mock_demo_employee', '演示员工', 2, 1, 0, 0, 15000, 0, 1), "
               "(3, 'mock_demo_walker', '演示路人', 3, NULL, 0, 0, 3000, 0, 1);")
    sql.append("INSERT INTO `usr_enterprises` (`id`, `enterprise_name`, `unified_social_credit_code`, "
               "`business_license_url`, `legal_person_name`, `legal_person_id_card_front`, "
               "`legal_person_id_card_back`, `contact_name`, `contact_phone`, `enterprise_type`, "
               "`member_level`, `audit_status`, `status`) VALUES "
               "(1, '演示科技有限公司', 'DEMO91440300MA5X000001', 'https://demo/license.png', '张三', "
               "'https://demo/id_front.png', 'https://demo/id_back.png', '李四', '13800000001', 1, 2, 1, 1);")
    sql.append("INSERT INTO `usr_enterprise_members` (`id`, `enterprise_id`, `user_id`, `role`, "
               "`invite_status`) VALUES (1, 1, 1, 1, 1), (2, 1, 2, 0, 1);")

    return sql


def main():
    parser = argparse.ArgumentParser(description="开发种子数据生成（仅开发环境使用，生产勿执行）")
    parser.add_argument("--host", default=os.environ.get("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("MYSQL_PORT", "3306")))
    parser.add_argument("--user", default=os.environ.get("MYSQL_USER", "root"))
    parser.add_argument("--password", default=os.environ.get("MYSQL_PASSWORD", "123456"))
    parser.add_argument("--db", default=os.environ.get("DB_NAME", "lease_db"))
    args = parser.parse_args()

    mysql_args = build_mysql_args(args.host, args.port, args.user, args.password)
    statements = build_sql()
    print(f"==> 灌入开发种子数据到 {args.db}（{len(statements)} 段 SQL）...")
    run_sql(mysql_args, args.db, "\n".join(statements))

    # 统计
    counts = subprocess.run(
        mysql_args + [args.db, "-N", "-e",
                      "SELECT CONCAT(table_name,'=',cnt) FROM ("
                      "SELECT 'prd_categories' table_name, COUNT(*) cnt FROM prd_categories UNION ALL "
                      "SELECT 'prd_products', COUNT(*) FROM prd_products UNION ALL "
                      "SELECT 'prd_spec_groups', COUNT(*) FROM prd_spec_groups UNION ALL "
                      "SELECT 'prd_spec_values', COUNT(*) FROM prd_spec_values UNION ALL "
                      "SELECT 'prd_skus', COUNT(*) FROM prd_skus UNION ALL "
                      "SELECT 'prd_daily_menus', COUNT(*) FROM prd_daily_menus UNION ALL "
                      "SELECT 'mtg_rooms', COUNT(*) FROM mtg_rooms UNION ALL "
                      "SELECT 'mtg_room_level_prices', COUNT(*) FROM mtg_room_level_prices UNION ALL "
                      "SELECT 'trd_recharge_tiers', COUNT(*) FROM trd_recharge_tiers UNION ALL "
                      "SELECT 'usr_member_levels', COUNT(*) FROM usr_member_levels UNION ALL "
                      "SELECT 'usr_admins', COUNT(*) FROM usr_admins UNION ALL "
                      "SELECT 'usr_users', COUNT(*) FROM usr_users UNION ALL "
                      "SELECT 'usr_enterprises', COUNT(*) FROM usr_enterprises) t ORDER BY 1;"],
        capture_output=True, text=True, check=True)
    print("==> 种子数据统计：")
    for line in counts.stdout.strip().splitlines():
        print("   ", line)
    print("==> 完成。演示账号：admin / 123456；演示用户 openid：mock_demo_admin / mock_demo_employee / mock_demo_walker")


if __name__ == "__main__":
    sys.exit(main())
