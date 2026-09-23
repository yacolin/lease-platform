#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# ============================================================================
# 开发登录用户（mock_dev_user）演示数据 —— 叠加在 db/seed.py 基础种子之上
# ----------------------------------------------------------------------------
# 背景：开发环境微信登录固定复用 wechat.mock-openid（默认 mock_dev_user），
#       但 db/seed.py 的基础种子刻意不创建该用户——集成测试把它当「干净新用户」
#       （断言昵称=微信用户 / userType=3 / memberLevel=0 / 余额=0 / 无企业 / 无券 /
#       无订单 / 无通知），直接给它灌数据会让 UsrAuth/OrdOrder/MktCoupon/Sys 等
#       集成测试失败。
#
# 用途：本脚本按需给 mock_dev_user 配套一整套演示数据，方便本地登录小程序后
#       各页面都有内容可看：
#         * 企业：已审核「开发调试科技有限公司」+ 1 名员工（企业管理员 = mock_dev_user）
#         * 会员：企业 VIP（含 4h/月会议室免费时长），个人 member_level=2
#         * 账户：可用余额 / 赠送余额 / 冻结余额 + 完整余额流水（充值/消费/退款/冻结/解冻）
#         * 充值：2 笔成功（含赠送）+ 1 笔待支付；会员购买记录 1 笔
#         * 订单：咖啡 5 单（已完成/待取餐/制作中/待支付/已取消退款）+ 明细 + 状态历史
#         * 正餐：2 笔预订（待备餐/待支付）+ 菜品快照
#         * 会议室：4 笔预约（上月已完成 / 本月免费已确认 / 超时付费已确认 / 待确认冻结）
#         * 营销：4 张用户券（未使用 / 已使用 / 已过期）+ 支付单 / 退款单
#         * 系统：5 条站内通知（已读/未读）
#
# 重要约定：
#   * 不改 db/seed.py，也不改变 make db-reset / make test 的默认行为；
#     跑集成测试前请保持「基础种子」状态（make db-reset，不要叠加本脚本）。
#   * 幂等：先按 openid / 统一社会信用代码清理 mock_dev_user 及其企业相关数据，再插入；
#     重复执行结果一致（会覆盖开发登录自动创建的裸用户）。
#   * 依赖基础种子（商品/SKU/会议室/充值档位/会员等级/券模板），须在 db/seed.py 之后运行。
#
# 用法：
#   make db-seed-dev                 # 在现有库上叠加演示数据（先 make db-seed / db-reset）
#   make db-reset-dev                # 建表 + 基础种子 + 本脚本（开发一步到位）
#   python3 db/seed_dev_user.py [--db lease_db --password 123456 ...]
# 环境变量与 db/seed.py 一致：DB_NAME / MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD
# ============================================================================
import argparse
import json
import os
import subprocess
import sys
import tempfile
from datetime import date, datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP

MOCK_OPENID = "mock_dev_user"
EMPLOYEE_OPENID = "mock_dev_employee"
CREDIT_CODE = "DEV91440300MA5X000002"

# 折扣率（与基础种子一致）：会员 VIP 0.90；最新一次成功充值档位=档位 6（0.78）
MEMBER_RATE = Decimal("0.90")
RECHARGE_RATE = Decimal("0.78")

# 主键采用与 MyBatis-Plus 雪花同量级但固定的开发段（9.9e17），避免与业务表种子 id 冲突；
# 业务运行时新生成的雪花 id（约 1.9e18）会大于本段，列表排序/「最近一次充值」仍以运行时数据为准。
ID_BASE = 990000000000000000


def did(n):
    """开发段固定主键：990000000000000000 + n。"""
    return ID_BASE + n


# 固定 id
USER_ID = did(1)
EMPLOYEE_ID = did(2)
ENTERPRISE_ID = did(3)
MEMBER_ADMIN_ID = did(4)
MEMBER_EMPLOYEE_ID = did(5)
PURCHASE_ID = did(10)

ORDER_COFFEE_DONE = did(101)
ORDER_COFFEE_PICKUP = did(102)
ORDER_COFFEE_MAKING = did(103)
ORDER_COFFEE_PENDING = did(104)
ORDER_COFFEE_CANCELLED = did(105)
ORDER_MEAL_READY = did(106)
ORDER_MEAL_PENDING = did(107)
ORDER_MEETING_PAID = did(108)
ORDER_MEETING_PENDING = did(109)

MEAL_RES_READY = did(301)
MEAL_RES_PENDING = did(302)

MTG_RES_PAST = did(501)
MTG_RES_FREE = did(502)
MTG_RES_PAID = did(503)
MTG_RES_PENDING = did(504)

RECHARGE_1 = did(901)
RECHARGE_2 = did(902)
RECHARGE_3 = did(903)

REFUND_ORDER = did(801)

# 券模板（db/seed.py）：1=满30减5咖啡券, 2=正餐9折券, 3=无门槛5元券
COUPON_TEMPLATE_FULL_30 = 1
COUPON_TEMPLATE_MEAL_90 = 2
COUPON_TEMPLATE_NO_THRESHOLD = 3

# 订单状态（与 OrderService / MealReservationService / MeetingReservationService 对齐）
ORDER_PENDING = 0
ORDER_PAID = 1
ORDER_MAKING = 2
ORDER_COMPLETED = 3
ORDER_CANCELLED = 4
MTG_PENDING = 0
MTG_CONFIRMED = 1
MTG_COMPLETED = 3

# 业务类型 / 操作人（与 PaymentService / OrderStatusHistoryService 对齐）
BIZ_RECHARGE = 1
BIZ_ORDER = 2
BIZ_MEAL = 3
BIZ_MEMBER = 4
BIZ_MEETING = 5
OP_USER = 1
OP_ADMIN = 2

ADMIN_ID = 1  # usr_admins 种子 admin id=1


# ---------------------------------------------------------------------------
# SQL 工具（与 db/seed.py 同风格，仅依赖 mysql CLI）
# ---------------------------------------------------------------------------
def esc(v):
    return str(v).replace("\\", "\\\\").replace("'", "''")


def s(v):
    """SQL 字符串字面量。"""
    return "'" + esc(v) + "'"


def sn(v):
    """字符串或 NULL。"""
    return "NULL" if v is None else s(v)


def j(v):
    """JSON 字面量或 NULL。"""
    return "NULL" if v is None else s(json.dumps(v, ensure_ascii=False))


def dt(v):
    """DATETIME 字面量或 NULL。"""
    return "NULL" if v is None else s(v.strftime("%Y-%m-%d %H:%M:%S"))


def dl(v):
    """DATE 字面量或 NULL。"""
    return "NULL" if v is None else s(v.isoformat())


def tm(v):
    """TIME 字面量或 NULL。"""
    return "NULL" if v is None else s(v.strftime("%H:%M:%S"))


def round_cents(value):
    """金额（分）乘折扣率后舍入到整数分（HALF_UP，与 Java roundCents 一致）。"""
    return int(Decimal(value).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def insert_stmt(table, columns, rows):
    return (f"INSERT INTO `{table}` ({', '.join('`' + c + '`' for c in columns)}) "
            f"VALUES {', '.join(rows)};")


# ---------------------------------------------------------------------------
# 金额计算（复刻 OrderService / MealReservationService 的折扣叠加链）
# ---------------------------------------------------------------------------
def coffee_order_amounts(items):
    """items: [(price, qty, ...)] → 与原价/会员/充值/应付/明细折后单价。

    应付 = 原价 × 会员折扣率（逐件舍入）× 充值折扣率；无优惠券。
    """
    total = sum(price * qty for price, qty, _ in items)
    discounted = [round_cents(Decimal(price) * MEMBER_RATE * RECHARGE_RATE) for price, _, _ in items]
    discounted_subtotal = sum(dp * qty for dp, (_, qty, _) in zip(discounted, items))
    after_member = round_cents(Decimal(total) * MEMBER_RATE)
    member_discount = total - after_member
    recharge_discount = total - member_discount - discounted_subtotal
    return {
        "total": total,
        "discounted": discounted,
        "discounted_subtotal": discounted_subtotal,
        "member_discount": member_discount,
        "recharge_discount": recharge_discount,
        "payable": discounted_subtotal,
        "discount": total - discounted_subtotal,
    }


def meal_order_amounts(price, qty, coupon=None, delivery_fee=0):
    """正餐预订金额：套餐 + 可选优惠券（在会员×充值折后价上再抵扣），配送费不参与优惠。"""
    total = price * qty
    discounted_price = round_cents(Decimal(price) * MEMBER_RATE * RECHARGE_RATE)
    discounted_subtotal = discounted_price * qty
    after_member = round_cents(Decimal(total) * MEMBER_RATE)
    member_discount = total - after_member
    recharge_discount = total - member_discount - discounted_subtotal
    coupon_discount = 0
    if coupon is not None:
        if coupon["type"] == 1:  # 满减
            coupon_discount = min(coupon["amount"], discounted_subtotal)
        else:                    # 折扣
            coupon_discount = round_cents(
                Decimal(discounted_subtotal) * (Decimal("1") - Decimal(str(coupon["rate"]))))
    payable = discounted_subtotal + delivery_fee - coupon_discount
    return {
        "total": total,
        "discounted_price": discounted_price,
        "discounted_subtotal": discounted_subtotal,
        "member_discount": member_discount,
        "recharge_discount": recharge_discount,
        "coupon_discount": coupon_discount,
        "payable": payable,
        "discount": total - discounted_subtotal + coupon_discount,
    }


def build_cleanup_sql():
    """清理 mock_dev_user / mock_dev_employee 及其企业关联的既有数据（含登录自动创建的裸用户）。"""
    users = f"SELECT id FROM usr_users WHERE openid IN ({s(MOCK_OPENID)}, {s(EMPLOYEE_OPENID)})"
    ent = f"SELECT id FROM usr_enterprises WHERE unified_social_credit_code = {s(CREDIT_CODE)}"
    ent_of_users = (f"SELECT enterprise_id FROM usr_users "
                    f"WHERE openid IN ({s(MOCK_OPENID)}, {s(EMPLOYEE_OPENID)}) AND enterprise_id IS NOT NULL")
    stmts = [
        f"DELETE FROM `mtg_bookings` WHERE reservation_id IN (SELECT id FROM `mtg_reservations` WHERE user_id IN ({users}) OR enterprise_id IN ({ent}) OR enterprise_id IN ({ent_of_users}));",
        f"DELETE FROM `mtg_reservations` WHERE user_id IN ({users}) OR enterprise_id IN ({ent}) OR enterprise_id IN ({ent_of_users});",
        f"DELETE FROM `ord_order_status_history` WHERE order_id IN (SELECT id FROM `ord_orders` WHERE user_id IN ({users})) OR (biz_type = 2 AND order_id IN (SELECT id FROM `ord_meal_reservations` WHERE user_id IN ({users})));",
        f"DELETE FROM `ord_meal_reservation_items` WHERE reservation_id IN (SELECT id FROM `ord_meal_reservations` WHERE user_id IN ({users}));",
        f"DELETE FROM `ord_meal_reservations` WHERE user_id IN ({users});",
        f"DELETE FROM `ord_order_items` WHERE order_id IN (SELECT id FROM `ord_orders` WHERE user_id IN ({users}));",
        f"DELETE FROM `ord_orders` WHERE user_id IN ({users});",
        f"DELETE FROM `mkt_user_coupons` WHERE user_id IN ({users});",
        f"DELETE FROM `sys_notifications` WHERE user_id IN ({users});",
        f"DELETE FROM `trd_refunds` WHERE user_id IN ({users});",
        f"DELETE FROM `trd_payments` WHERE user_id IN ({users});",
        f"DELETE FROM `trd_recharge_records` WHERE user_id IN ({users});",
        f"DELETE FROM `trd_balance_transactions` WHERE user_id IN ({users});",
        f"DELETE FROM `acct_accounts` WHERE user_id IN ({users});",
        f"DELETE FROM `usr_member_purchases` WHERE enterprise_id IN ({ent}) OR enterprise_id IN ({ent_of_users});",
        f"DELETE FROM `usr_enterprise_members` WHERE user_id IN ({users}) OR enterprise_id IN ({ent}) OR enterprise_id IN ({ent_of_users});",
        f"DELETE FROM `usr_enterprises` WHERE unified_social_credit_code = {s(CREDIT_CODE)} OR id IN ({ent_of_users});",
        f"DELETE FROM `usr_users` WHERE openid IN ({s(MOCK_OPENID)}, {s(EMPLOYEE_OPENID)});",
    ]
    return stmts


def build_seed_sql():
    now = datetime.now().replace(microsecond=0)
    today = date.today()

    def at(day_offset, hh, mm, base=None):
        b = base if base is not None else now
        return (b + timedelta(days=day_offset)).replace(hour=hh, minute=mm, second=0, microsecond=0)

    member_start = today - timedelta(days=15)
    member_end = member_start + timedelta(days=365)
    member_expire_at = datetime.combine(member_end, datetime.min.time()).replace(hour=23, minute=59, second=59)
    # 上月某天（保证不计入本月免费时长）
    prev_month_day = today.replace(day=1) - timedelta(days=3)

    stmts = []

    # ---------- 用户 + 企业 + 成员关系 ----------
    stmts.append(insert_stmt(
        "usr_users",
        ["id", "openid", "nickname", "avatar_url", "phone", "user_type", "enterprise_id",
         "member_level", "is_enterprise_admin", "status", "last_login_at"],
        [
            f"({USER_ID}, {s(MOCK_OPENID)}, '开发调试用户', 'https://demo/avatar_dev_user.png', "
            f"'13800000000', 2, {ENTERPRISE_ID}, 2, 1, 1, {dt(now)})",
            f"({EMPLOYEE_ID}, {s(EMPLOYEE_OPENID)}, '开发调试员工', 'https://demo/avatar_dev_emp.png', "
            f"'13800000001', 2, {ENTERPRISE_ID}, 2, 0, 1, {dt(at(-1, 9, 30))})",
        ]))

    stmts.append(insert_stmt(
        "usr_enterprises",
        ["id", "enterprise_name", "unified_social_credit_code", "business_license_url",
         "legal_person_name", "legal_person_id_card_front", "legal_person_id_card_back",
         "contact_name", "contact_phone", "enterprise_type", "member_level", "member_expire_at",
         "audit_status", "audited_at", "status"],
        [f"({ENTERPRISE_ID}, '开发调试科技有限公司', {s(CREDIT_CODE)}, 'https://demo/dev_license.png', "
         f"'张伟', 'https://demo/dev_id_front.png', 'https://demo/dev_id_back.png', "
         f"'张伟', '13800000000', 2, 2, {dt(member_expire_at)}, 1, {dt(at(-20, 10, 0))}, 1)"]))

    stmts.append(insert_stmt(
        "usr_enterprise_members",
        ["id", "enterprise_id", "user_id", "role", "invite_status", "invited_at", "accepted_at"],
        [f"({MEMBER_ADMIN_ID}, {ENTERPRISE_ID}, {USER_ID}, 1, 1, {dt(at(-20, 10, 0))}, {dt(at(-20, 10, 5))})",
         f"({MEMBER_EMPLOYEE_ID}, {ENTERPRISE_ID}, {EMPLOYEE_ID}, 0, 1, {dt(at(-10, 9, 0))}, {dt(at(-10, 9, 10))})"]))

    # ---------- 会员购买（企业 VIP，mock 微信支付）+ 支付单 ----------
    purchase_no = "MP20260908DEV0001"
    purchase_out = "OT20260908DEV0001"
    stmts.append(insert_stmt(
        "usr_member_purchases",
        ["id", "purchase_no", "enterprise_id", "member_level_id", "original_price", "pay_price",
         "payment_method", "transaction_id", "out_trade_no", "start_date", "end_date",
         "payment_status", "paid_at", "created_at"],
        [f"({PURCHASE_ID}, {s(purchase_no)}, {ENTERPRISE_ID}, 2, 500000, 500000, 1, "
         f"{sn('mock_' + purchase_out)}, {s(purchase_out)}, {dl(member_start)}, {dl(member_end)}, 1, "
         f"{dt(at(-15, 10, 0))}, {dt(at(-15, 9, 59))})"]))

    # ---------- 充值记录（2 成功 + 1 待支付）----------
    rc1_out = "RC20260809DEV0001"
    rc2_out = "RC20260908DEV0002"
    rc3_out = "RC20260923DEV0003"
    stmts.append(insert_stmt(
        "trd_recharge_records",
        ["id", "user_id", "tier_id", "recharge_amount", "bonus_amount", "total_amount",
         "payment_method", "transaction_id", "out_trade_no", "payment_status", "paid_at", "created_at"],
        [
            f"({RECHARGE_1}, {USER_ID}, 3, 100000, 15000, 115000, 1, {sn('mock_' + rc1_out)}, "
            f"{s(rc1_out)}, 1, {dt(at(-45, 10, 0))}, {dt(at(-45, 9, 58))})",
            f"({RECHARGE_2}, {USER_ID}, 6, 800000, 200000, 1000000, 1, {sn('mock_' + rc2_out)}, "
            f"{s(rc2_out)}, 1, {dt(at(-15, 10, 0))}, {dt(at(-15, 9, 58))})",
            f"({RECHARGE_3}, {USER_ID}, 1, 20000, 2000, 22000, 1, NULL, {s(rc3_out)}, 0, NULL, {dt(at(0, 9, 50))})",
        ]))

    # ---------- 咖啡订单（5 单，覆盖全状态）+ 明细 ----------
    coffee_specs = {
        1: ("美式", 1200),
        2: ("拿铁", 1500),
        9: ("卡布奇诺", 2200),
        10: ("摩卡", 2500),
        13: ("燕麦拿铁", 2300),
        14: ("生椰拿铁", 2400),
    }
    coffee_orders = [
        # id, 状态, 下单时间, 支付时间, 完成时间, 明细 [(pid, price, qty, sku_id, sku_code, sku_spec, spec)]
        {"id": ORDER_COFFEE_DONE, "status": ORDER_COMPLETED, "no": "CO20260918DEV0001",
         "created": at(-5, 9, 15), "paid": at(-5, 9, 16), "completed": at(-5, 9, 40),
         "items": [(1, 1200, 2, 10001, "SKU000101",
                    {"cup_size": "大杯", "temperature": "热", "sugar": "无糖"},
                    {"cup_size": "大杯", "temperature": "热", "sugar": "无糖"}),
                   (2, 1500, 1, 10020, "SKU000208",
                    {"cup_size": "中杯", "temperature": "热", "sugar": "少糖"},
                    {"cup_size": "中杯", "temperature": "热", "sugar": "少糖"})],
         "pickup": "D90001", "remark": "少冰"},
        {"id": ORDER_COFFEE_PICKUP, "status": ORDER_PAID, "no": "CO20260922DEV0002",
         "created": at(-1, 8, 30), "paid": at(-1, 8, 31), "completed": None,
         "items": [(14, 2400, 1, None, None, None,
                    {"cup_size": "大杯", "temperature": "冰", "sugar": "无糖"})],
         "pickup": "D90002", "remark": None},
        {"id": ORDER_COFFEE_MAKING, "status": ORDER_MAKING, "no": "CO20260923DEV0003",
         "created": at(0, 8, 10), "paid": at(0, 8, 11), "completed": None,
         "items": [(13, 2300, 2, None, None, None,
                    {"cup_size": "大杯", "temperature": "热", "sugar": "无糖"})],
         "pickup": "D90003", "remark": "两杯都要杯套"},
        {"id": ORDER_COFFEE_PENDING, "status": ORDER_PENDING, "no": "CO20260923DEV0004",
         "created": at(0, 8, 5), "paid": None, "completed": None,
         "items": [(9, 2200, 1, None, None, None,
                    {"cup_size": "中杯", "temperature": "冰", "sugar": "正常"})],
         "pickup": None, "remark": None},
        {"id": ORDER_COFFEE_CANCELLED, "status": ORDER_CANCELLED, "no": "CO20260920DEV0005",
         "created": at(-3, 14, 0), "paid": at(-3, 14, 1), "completed": None,
         "items": [(10, 2500, 1, None, None, None,
                    {"cup_size": "大杯", "temperature": "热", "sugar": "正常"})],
         "pickup": "D90005", "remark": None},
    ]
    order_rows, item_rows, history_rows = [], [], []
    order_item_id = did(201)
    for o in coffee_orders:
        amounts = coffee_order_amounts([(price, qty, spec) for (_, price, qty, _, _, _, spec) in o["items"]])
        out_trade_no = None if o["paid"] is None else "PO" + o["no"][2:]
        order_rows.append(
            f"({o['id']}, {s(o['no'])}, {USER_ID}, {ENTERPRISE_ID}, 1, {o['status']}, "
            f"{amounts['total']}, {amounts['discount']}, {amounts['member_discount']}, "
            f"{amounts['recharge_discount']}, NULL, NULL, 0, {amounts['payable']}, 1, "
            f"{sn(out_trade_no)}, {sn(o['pickup'])}, NULL, 0, NULL, NULL, {sn(o['remark'])}, "
            f"{dt(o['paid'])}, {dt(o['completed'])}, "
            f"{dt(at(-3, 14, 20)) if o['id'] == ORDER_COFFEE_CANCELLED else 'NULL'}, "
            f"{sn('不想要了') if o['id'] == ORDER_COFFEE_CANCELLED else 'NULL'}, {dt(o['created'])})")
        for idx, (pid, price, qty, sku_id, sku_code, sku_spec, spec) in enumerate(o["items"]):
            dp = amounts["discounted"][idx]
            item_rows.append(
                f"({order_item_id}, {o['id']}, {pid}, {sku_id if sku_id else 'NULL'}, "
                f"{sn(sku_code)}, {sku_id and price or 'NULL'}, {j(sku_spec)}, "
                f"{s(coffee_specs[pid][0])}, {price}, {j(spec)}, {qty}, {price * qty}, {dp}, {dp * qty}, "
                f"{dt(o['created'])})")
            order_item_id += 1
        # 状态历史
        history_rows.append((1, o["id"], None, ORDER_PENDING, USER_ID, OP_USER, "下单", o["created"]))
        if o["paid"] is not None:
            history_rows.append((1, o["id"], ORDER_PENDING, ORDER_PAID, USER_ID, OP_USER, "余额支付", o["paid"]))
        if o["status"] == ORDER_MAKING:
            history_rows.append((1, o["id"], ORDER_PAID, ORDER_MAKING, ADMIN_ID, OP_ADMIN, "商家开始制作", at(0, 8, 15)))
        if o["status"] == ORDER_COMPLETED:
            history_rows.append((1, o["id"], ORDER_PAID, ORDER_MAKING, ADMIN_ID, OP_ADMIN, "商家开始制作", at(-5, 9, 20)))
            history_rows.append((1, o["id"], ORDER_MAKING, ORDER_COMPLETED, ADMIN_ID, OP_ADMIN, "制作完成", o["completed"]))
        if o["status"] == ORDER_CANCELLED:
            history_rows.append((1, o["id"], ORDER_PAID, ORDER_CANCELLED, USER_ID, OP_USER, "不想要了", at(-3, 14, 20)))

    stmts.append(insert_stmt(
        "ord_orders",
        ["id", "order_no", "user_id", "enterprise_id", "order_type", "order_status",
         "total_amount", "discount_amount", "member_discount", "recharge_discount",
         "coupon_id", "coupon_name_snapshot", "coupon_discount", "payable_amount",
         "payment_method", "out_trade_no", "pickup_code", "delivery_type", "delivery_fee",
         "reservation_date", "reservation_time", "remark", "paid_at", "completed_at",
         "cancelled_at", "cancel_reason", "created_at"],
        order_rows))

    stmts.append(insert_stmt(
        "ord_order_items",
        ["id", "order_id", "product_id", "sku_id", "sku_name_snapshot", "sku_price_snapshot",
         "specification_snapshot", "product_name", "product_price", "specification", "quantity",
         "subtotal", "discounted_price", "discounted_subtotal", "created_at"],
        item_rows))

    # ---------- 正餐预订（2 笔）+ 关联订单 + 明细 ----------
    meal_coupon = {"type": 2, "rate": "0.90"}  # 正餐9折券（模板 2）
    m1 = meal_order_amounts(2000, 2, coupon=meal_coupon, delivery_fee=0)
    m2 = meal_order_amounts(2500, 1, coupon=None, delivery_fee=500)
    meal_menu_date_1 = today + timedelta(days=1)
    meal_menu_date_2 = today + timedelta(days=2)
    coupon_used_id = did(1102)

    stmts.append(insert_stmt(
        "ord_orders",
        ["id", "order_no", "user_id", "enterprise_id", "order_type", "order_status",
         "total_amount", "discount_amount", "member_discount", "recharge_discount",
         "coupon_id", "coupon_name_snapshot", "coupon_discount", "payable_amount",
         "payment_method", "delivery_type", "delivery_fee", "reservation_date",
         "reservation_time", "remark", "paid_at", "completed_at", "created_at"],
        [
            f"({ORDER_MEAL_READY}, 'MO20260921DEV0001', {USER_ID}, {ENTERPRISE_ID}, 2, {ORDER_PAID}, "
            f"{m1['total']}, {m1['discount']}, {m1['member_discount']}, {m1['recharge_discount']}, "
            f"{coupon_used_id}, '正餐9折券', {m1['coupon_discount']}, {m1['payable']}, 1, 1, 0, "
            f"{dl(meal_menu_date_1)}, '11:30:00', '少辣', {dt(at(-2, 11, 0))}, NULL, {dt(at(-2, 10, 55))})",
            f"({ORDER_MEAL_PENDING}, 'MO20260923DEV0002', {USER_ID}, {ENTERPRISE_ID}, 2, {ORDER_PENDING}, "
            f"{m2['total']}, {m2['discount']}, {m2['member_discount']}, {m2['recharge_discount']}, "
            f"NULL, NULL, 0, {m2['payable']}, 1, 3, 500, "
            f"{dl(meal_menu_date_2)}, '17:30:00', NULL, NULL, NULL, {dt(at(0, 10, 30))})",
        ]))

    dish_4 = [{"dishName": "红烧肉", "dishType": 1, "sortOrder": 1},
              {"dishName": "宫保鸡丁", "dishType": 1, "sortOrder": 2},
              {"dishName": "鱼香肉丝", "dishType": 1, "sortOrder": 3},
              {"dishName": "清炒时蔬", "dishType": 2, "sortOrder": 4}]
    dish_5 = [{"dishName": "红烧排骨", "dishType": 1, "sortOrder": 1},
              {"dishName": "辣子鸡", "dishType": 1, "sortOrder": 2},
              {"dishName": "水煮牛肉", "dishType": 1, "sortOrder": 3},
              {"dishName": "梅菜扣肉", "dishType": 1, "sortOrder": 4},
              {"dishName": "蒜蓉西兰花", "dishType": 2, "sortOrder": 5}]

    stmts.append(insert_stmt(
        "ord_meal_reservations",
        ["id", "reservation_no", "user_id", "enterprise_id", "order_id", "product_id",
         "product_name", "menu_date", "reservation_date", "reservation_time_slot", "quantity",
         "delivery_type", "delivery_fee", "delivery_address", "total_amount", "discount_amount",
         "payable_amount", "payment_method", "out_trade_no", "status", "remark", "paid_at", "created_at"],
        [
            f"({MEAL_RES_READY}, 'RS20260921DEV0001', {USER_ID}, {ENTERPRISE_ID}, {ORDER_MEAL_READY}, 4, "
            f"'3荤1素套餐', {dl(meal_menu_date_1)}, {dl(today - timedelta(days=2))}, '午餐', 2, 1, 0, NULL, "
            f"{m1['total']}, {m1['discount']}, {m1['payable']}, 1, 'PO20260921DEV0001', {ORDER_PAID}, '少辣', "
            f"{dt(at(-2, 11, 0))}, {dt(at(-2, 10, 55))})",
            f"({MEAL_RES_PENDING}, 'RS20260923DEV0002', {USER_ID}, {ENTERPRISE_ID}, {ORDER_MEAL_PENDING}, 5, "
            f"'4荤1素套餐', {dl(meal_menu_date_2)}, {dl(today)}, '晚餐', 1, 3, 500, '3号楼501', "
            f"{m2['total']}, {m2['discount']}, {m2['payable']}, 1, NULL, {ORDER_PENDING}, NULL, NULL, "
            f"{dt(at(0, 10, 30))})",
        ]))

    stmts.append(insert_stmt(
        "ord_meal_reservation_items",
        ["id", "reservation_id", "product_id", "product_name", "product_price", "quantity",
         "subtotal", "discounted_price", "discounted_subtotal", "dish_details", "created_at"],
        [
            f"({did(401)}, {MEAL_RES_READY}, 4, '3荤1素套餐', 2000, 2, {m1['total']}, "
            f"{m1['discounted_price']}, {m1['discounted_subtotal']}, {j(dish_4)}, {dt(at(-2, 10, 55))})",
            f"({did(402)}, {MEAL_RES_PENDING}, 5, '4荤1素套餐', 2500, 1, {m2['total']}, "
            f"{m2['discounted_price']}, {m2['discounted_subtotal']}, {j(dish_5)}, {dt(at(0, 10, 30))})",
        ]))

    # ---------- 会议室预约（4 笔）+ 占用记录 ----------
    mtg_free_1_start, mtg_free_1_end = datetime.combine(prev_month_day, datetime.min.time()).replace(hour=9), \
        datetime.combine(prev_month_day, datetime.min.time()).replace(hour=11)
    mtg_free_2_date = today + timedelta(days=2)
    mtg_paid_date = today + timedelta(days=4)
    mtg_pending_date = today + timedelta(days=3)

    stmts.append(insert_stmt(
        "mtg_reservations",
        ["id", "reservation_no", "room_id", "user_id", "enterprise_id", "order_id",
         "reservation_date", "start_time", "end_time", "duration_hours", "meeting_topic",
         "status", "is_free", "fee_amount", "overtime_unit_price", "free_hours_deducted",
         "cancelled_at", "cancel_reason", "created_at"],
        [
            f"({MTG_RES_PAST}, 'MR20260815DEV0001', 1, {USER_ID}, {ENTERPRISE_ID}, NULL, "
            f"{dl(prev_month_day)}, '09:00:00', '11:00:00', 2.0, '上月部门周会', {MTG_COMPLETED}, 1, 0, 8000, 2.0, "
            f"NULL, NULL, {dt(at(-38, 10, 0))})",
            f"({MTG_RES_FREE}, 'MR20260922DEV0002', 1, {USER_ID}, {ENTERPRISE_ID}, NULL, "
            f"{dl(mtg_free_2_date)}, '09:00:00', '13:00:00', 4.0, '产品评审会', {MTG_CONFIRMED}, 1, 0, 8000, 4.0, "
            f"NULL, NULL, {dt(at(-1, 11, 0))})",
            f"({MTG_RES_PAID}, 'MR20260922DEV0003', 2, {USER_ID}, {ENTERPRISE_ID}, {ORDER_MEETING_PAID}, "
            f"{dl(mtg_paid_date)}, '14:00:00', '16:00:00', 2.0, '客户方案沟通', {MTG_CONFIRMED}, 0, 16000, 8000, 0.0, "
            f"NULL, NULL, {dt(at(-1, 10, 0))})",
            f"({MTG_RES_PENDING}, 'MR20260923DEV0004', 3, {USER_ID}, {ENTERPRISE_ID}, {ORDER_MEETING_PENDING}, "
            f"{dl(mtg_pending_date)}, '16:00:00', '17:00:00', 1.0, '招聘面试', {MTG_PENDING}, 0, 8000, 8000, 0.0, "
            f"NULL, NULL, {dt(at(0, 9, 0))})",
        ]))

    stmts.append(insert_stmt(
        "mtg_bookings",
        ["id", "room_id", "reservation_id", "start_at", "end_at", "status"],
        [
            f"({did(601)}, 1, {MTG_RES_PAST}, {dt(mtg_free_1_start)}, {dt(mtg_free_1_end)}, 1)",
            f"({did(602)}, 1, {MTG_RES_FREE}, {dt(datetime.combine(mtg_free_2_date, datetime.min.time()).replace(hour=9))}, "
            f"{dt(datetime.combine(mtg_free_2_date, datetime.min.time()).replace(hour=13))}, 0)",
            f"({did(603)}, 2, {MTG_RES_PAID}, {dt(datetime.combine(mtg_paid_date, datetime.min.time()).replace(hour=14))}, "
            f"{dt(datetime.combine(mtg_paid_date, datetime.min.time()).replace(hour=16))}, 0)",
            f"({did(604)}, 3, {MTG_RES_PENDING}, {dt(datetime.combine(mtg_pending_date, datetime.min.time()).replace(hour=16))}, "
            f"{dt(datetime.combine(mtg_pending_date, datetime.min.time()).replace(hour=17))}, 0)",
        ]))

    # 会议室关联订单（order_type=4）
    meeting_paid_out = "RO20260922DEV0003"
    meeting_pending_out = "RO20260923DEV0004"
    stmts.append(insert_stmt(
        "ord_orders",
        ["id", "order_no", "user_id", "enterprise_id", "order_type", "order_status",
         "total_amount", "discount_amount", "member_discount", "recharge_discount",
         "coupon_discount", "payable_amount", "payment_method", "out_trade_no",
         "reservation_date", "reservation_time", "remark", "paid_at", "created_at"],
        [
            f"({ORDER_MEETING_PAID}, {s(meeting_paid_out)}, {USER_ID}, {ENTERPRISE_ID}, 4, {ORDER_PAID}, "
            f"16000, 0, 0, 0, 0, 16000, 1, {s(meeting_paid_out)}, {dl(mtg_paid_date)}, '14:00:00', "
            f"'客户方案沟通', {dt(at(-1, 10, 5))}, {dt(at(-1, 10, 0))})",
            f"({ORDER_MEETING_PENDING}, {s(meeting_pending_out)}, {USER_ID}, {ENTERPRISE_ID}, 4, {ORDER_PENDING}, "
            f"8000, 0, 0, 0, 0, 8000, 1, NULL, {dl(mtg_pending_date)}, '16:00:00', "
            f"'招聘面试', NULL, {dt(at(0, 9, 0))})",
        ]))

    # ---------- 优惠券（4 张用户券）----------
    stmts.append(insert_stmt(
        "mkt_user_coupons",
        ["id", "coupon_id", "user_id", "coupon_name", "coupon_type", "discount_amount",
         "discount_rate", "threshold_amount", "biz_type", "status", "order_id", "used_at",
         "expire_at", "created_at"],
        [
            # 未使用：无门槛 5 元券（模板 3）
            f"({did(1101)}, {COUPON_TEMPLATE_NO_THRESHOLD}, {USER_ID}, '无门槛5元券', 1, 500, NULL, 0, NULL, "
            f"0, NULL, NULL, {dt(at(20, 9, 0))}, {dt(at(-5, 9, 0))})",
            # 已使用：正餐 9 折券（模板 2），绑定 M1 订单
            f"({coupon_used_id}, {COUPON_TEMPLATE_MEAL_90}, {USER_ID}, '正餐9折券', 2, NULL, 0.90, 0, 2, "
            f"1, {ORDER_MEAL_READY}, {dt(at(-2, 10, 55))}, {dt(at(25, 9, 0))}, {dt(at(-2, 9, 0))})",
            # 已过期：满 30 减 5 咖啡券（模板 1）
            f"({did(1103)}, {COUPON_TEMPLATE_FULL_30}, {USER_ID}, '满30减5咖啡券', 1, 500, NULL, 3000, 1, "
            f"2, NULL, NULL, {dt(at(-3, 9, 0))}, {dt(at(-40, 9, 0))})",
            # 未使用：满 30 减 5 咖啡券（模板 1）
            f"({did(1104)}, {COUPON_TEMPLATE_FULL_30}, {USER_ID}, '满30减5咖啡券', 1, 500, NULL, 3000, 1, "
            f"0, NULL, NULL, {dt(at(27, 9, 0))}, {dt(at(-3, 9, 1))})",
        ]))

    # ---------- 支付单 ----------
    payment_rows = [
        # 充值 R1（微信 mock，成功）
        f"({did(701)}, 'PAY20260809DEV0001', {USER_ID}, {BIZ_RECHARGE}, {RECHARGE_1}, 100000, 2, 3, 1, "
        f"{s(rc1_out)}, {sn('mock_' + rc1_out)}, {dt(at(-45, 10, 0))}, {dt(at(-45, 9, 58))})",
        # 充值 R2（微信 mock，成功）
        f"({did(702)}, 'PAY20260908DEV0002', {USER_ID}, {BIZ_RECHARGE}, {RECHARGE_2}, 800000, 2, 3, 1, "
        f"{s(rc2_out)}, {sn('mock_' + rc2_out)}, {dt(at(-15, 10, 0))}, {dt(at(-15, 9, 58))})",
        # 充值 R3（待支付）
        f"({did(703)}, 'PAY20260923DEV0003', {USER_ID}, {BIZ_RECHARGE}, {RECHARGE_3}, 20000, 2, 3, 0, "
        f"{s(rc3_out)}, NULL, NULL, {dt(at(0, 9, 50))})",
        # 咖啡订单（余额支付）
        f"({did(704)}, 'PAY20260918DEV0004', {USER_ID}, {BIZ_ORDER}, {ORDER_COFFEE_DONE}, 2737, 1, 2, 1, "
        f"'PO20260918DEV0001', NULL, {dt(at(-5, 9, 16))}, {dt(at(-5, 9, 15))})",
        f"({did(705)}, 'PAY20260922DEV0005', {USER_ID}, {BIZ_ORDER}, {ORDER_COFFEE_PICKUP}, 1685, 1, 2, 1, "
        f"'PO20260922DEV0002', NULL, {dt(at(-1, 8, 31))}, {dt(at(-1, 8, 30))})",
        f"({did(706)}, 'PAY20260923DEV0006', {USER_ID}, {BIZ_ORDER}, {ORDER_COFFEE_MAKING}, 3230, 1, 2, 1, "
        f"'PO20260923DEV0003', NULL, {dt(at(0, 8, 11))}, {dt(at(0, 8, 10))})",
        # 咖啡订单已取消退款 → 支付单已退款
        f"({did(707)}, 'PAY20260920DEV0007', {USER_ID}, {BIZ_ORDER}, {ORDER_COFFEE_CANCELLED}, 1755, 1, 2, 4, "
        f"'PO20260920DEV0005', NULL, {dt(at(-3, 14, 1))}, {dt(at(-3, 14, 0))})",
        # 正餐预订（余额支付，已支付）
        f"({did(708)}, 'PAY20260921DEV0008', {USER_ID}, {BIZ_MEAL}, {MEAL_RES_READY}, {m1['payable']}, 1, 2, 1, "
        f"'PO20260921DEV0001', NULL, {dt(at(-2, 11, 0))}, {dt(at(-2, 10, 55))})",
        # 会议室订单（余额支付，已支付 / 待支付）
        f"({did(709)}, 'PAY20260922DEV0009', {USER_ID}, {BIZ_MEETING}, {MTG_RES_PAID}, 16000, 1, 2, 1, "
        f"{s(meeting_paid_out)}, NULL, {dt(at(-1, 10, 5))}, {dt(at(-1, 10, 0))})",
        f"({did(710)}, 'PAY20260923DEV0010', {USER_ID}, {BIZ_MEETING}, {MTG_RES_PENDING}, 8000, 1, 2, 0, "
        f"{s(meeting_pending_out)}, NULL, NULL, {dt(at(0, 9, 0))})",
        # 会员购买（微信 mock，成功）
        f"({did(711)}, 'PAY20260908DEV0011', {USER_ID}, {BIZ_MEMBER}, {PURCHASE_ID}, 500000, 2, 3, 1, "
        f"{s(purchase_out)}, {sn('mock_' + purchase_out)}, {dt(at(-15, 10, 0))}, {dt(at(-15, 9, 59))})",
    ]
    stmts.append(insert_stmt(
        "trd_payments",
        ["id", "payment_no", "user_id", "biz_type", "biz_id", "amount", "payment_method",
         "payment_channel", "status", "out_trade_no", "transaction_id", "paid_at", "created_at"],
        payment_rows))

    # ---------- 退款单（O5 取消退款）----------
    stmts.append(insert_stmt(
        "trd_refunds",
        ["id", "refund_no", "payment_id", "user_id", "biz_type", "biz_id", "refund_amount",
         "refund_method", "status", "idempotency_key", "refund_reason", "refunded_at", "created_at"],
        [f"({REFUND_ORDER}, 'RF20260920DEV0001', {did(707)}, {USER_ID}, {BIZ_ORDER}, "
         f"{ORDER_COFFEE_CANCELLED}, 1755, 1, 1, {s('ORDER_CANCEL_REFUND:' + str(ORDER_COFFEE_CANCELLED))}, "
         f"'订单取消退款', {dt(at(-3, 14, 20))}, {dt(at(-3, 14, 20))})"]))

    # ---------- 账户 + 余额流水（按时间顺序推演，保证前后余额自洽）----------
    # (时间, 类型, 可用增减, 赠送增减, 冻结增减, 流水金额, 关联订单, 关联充值, 备注)
    ledger_ops = [
        (at(-45, 10, 0), 1, 100000, 15000, 0, 115000, None, RECHARGE_1, "余额充值"),
        (at(-15, 10, 0), 1, 800000, 200000, 0, 1000000, None, RECHARGE_2, "余额充值"),
        (at(-5, 9, 16), 2, 0, -2737, 0, -2737, ORDER_COFFEE_DONE, None, "咖啡订单"),
        (at(-3, 14, 1), 2, 0, -1755, 0, -1755, ORDER_COFFEE_CANCELLED, None, "咖啡订单"),
        (at(-3, 14, 20), 3, 1755, 0, 0, 1755, ORDER_COFFEE_CANCELLED, None, "订单取消退款"),
        (at(-2, 11, 0), 2, 0, -m1["payable"], 0, -m1["payable"], ORDER_MEAL_READY, None, "正餐预订"),
        (at(-1, 8, 31), 2, 0, -1685, 0, -1685, ORDER_COFFEE_PICKUP, None, "咖啡订单"),
        (at(-1, 10, 0), 6, -16000, 0, 16000, -16000, ORDER_MEETING_PAID, None, "会议室预约预授权"),
        (at(-1, 10, 5), 7, 16000, 0, -16000, 16000, ORDER_MEETING_PAID, None, "会议室预约支付解冻"),
        (at(-1, 10, 5), 2, 0, -16000, 0, -16000, ORDER_MEETING_PAID, None, "会议室预约"),
        (at(0, 8, 11), 2, 0, -3230, 0, -3230, ORDER_COFFEE_MAKING, None, "咖啡订单"),
        (at(0, 9, 0), 6, -8000, 0, 8000, -8000, ORDER_MEETING_PENDING, None, "会议室预约预授权"),
    ]
    available = gift = frozen = 0
    ledger_rows = []
    for idx, (when, tx_type, d_avail, d_gift, d_frozen, amount, order_id, recharge_id, remark) in enumerate(ledger_ops):
        a0, g0, f0 = available, gift, frozen
        available += d_avail
        gift += d_gift
        frozen += d_frozen
        ledger_rows.append(
            f"({did(1001 + idx)}, {USER_ID}, {tx_type}, {amount}, {a0}, {available}, {g0}, {gift}, "
            f"{f0}, {frozen}, {order_id if order_id else 'NULL'}, {recharge_id if recharge_id else 'NULL'}, "
            f"{sn(remark)}, {dt(when)})")

    stmts.append(insert_stmt(
        "acct_accounts",
        ["id", "user_id", "available_balance", "gift_balance", "frozen_balance", "status"],
        [f"({USER_ID}, {USER_ID}, {available}, {gift}, {frozen}, 1)"]))
    stmts.append(insert_stmt(
        "trd_balance_transactions",
        ["id", "user_id", "transaction_type", "amount", "balance_before", "balance_after",
         "gift_balance_before", "gift_balance_after", "frozen_balance_before", "frozen_balance_after",
         "related_order_id", "related_recharge_id", "remark", "created_at"],
        ledger_rows))

    # ---------- 订单状态历史（咖啡 / 正餐 / 会议室订单）----------
    history_rows.append((2, MEAL_RES_READY, None, ORDER_PENDING, USER_ID, OP_USER, "预订下单", at(-2, 10, 55)))
    history_rows.append((2, MEAL_RES_READY, ORDER_PENDING, ORDER_PAID, USER_ID, OP_USER, "余额支付", at(-2, 11, 0)))
    history_rows.append((2, MEAL_RES_PENDING, None, ORDER_PENDING, USER_ID, OP_USER, "预订下单", at(0, 10, 30)))
    history_rows.append((3, ORDER_MEETING_PAID, None, ORDER_PENDING, USER_ID, OP_USER, "会议室预约下单", at(-1, 10, 0)))
    history_rows.append((3, ORDER_MEETING_PAID, ORDER_PENDING, ORDER_PAID, USER_ID, OP_USER, "会议室预约支付", at(-1, 10, 5)))
    history_rows.append((3, ORDER_MEETING_PENDING, None, ORDER_PENDING, USER_ID, OP_USER, "会议室预约下单", at(0, 9, 0)))
    history_rows.sort(key=lambda r: r[7])
    stmts.append(insert_stmt(
        "ord_order_status_history",
        ["id", "order_id", "biz_type", "from_status", "to_status", "operator_id",
         "operator_type", "reason", "created_at"],
        [f"({did(1301 + i)}, {r[1]}, {r[0]}, {r[2] if r[2] is not None else 'NULL'}, {r[3]}, {r[4]}, "
         f"{r[5]}, {sn(r[6])}, {dt(r[7])})" for i, r in enumerate(history_rows)]))

    # ---------- 站内通知（5 条，含未读）----------
    stmts.append(insert_stmt(
        "sys_notifications",
        ["user_id", "notification_type", "title", "content", "send_status", "send_time", "is_read", "created_at"],
        [
            f"({USER_ID}, 1, '企业审核通过', '您的企业「开发调试科技有限公司」已通过实名认证审核', 1, "
            f"{dt(at(-20, 10, 5))}, 1, {dt(at(-20, 10, 5))})",
            f"({USER_ID}, 3, '充值成功', '充值 ¥8000.00 已到账，赠送 ¥2000.00', 1, "
            f"{dt(at(-15, 10, 0))}, 1, {dt(at(-15, 10, 0))})",
            f"({USER_ID}, 4, '订单已完成', '您的咖啡订单 CO20260918DEV0001 已完成，感谢惠顾', 1, "
            f"{dt(at(-5, 9, 40))}, 1, {dt(at(-5, 9, 40))})",
            f"({USER_ID}, 5, '会议室预约成功', '「产品评审会」已预约成功，请准时使用', 1, "
            f"{dt(at(-1, 11, 0))}, 0, {dt(at(-1, 11, 0))})",
            f"({USER_ID}, 6, '员工已加入', '开发调试员工 已接受邀请加入您的企业', 1, "
            f"{dt(at(-10, 9, 10))}, 0, {dt(at(-10, 9, 10))})",
        ]))

    return stmts


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


def main():
    parser = argparse.ArgumentParser(
        description="开发登录用户（mock_dev_user）演示数据，叠加在 db/seed.py 基础种子之上")
    parser.add_argument("--host", default=os.environ.get("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("MYSQL_PORT", "3306")))
    parser.add_argument("--user", default=os.environ.get("MYSQL_USER", "root"))
    parser.add_argument("--password", default=os.environ.get("MYSQL_PASSWORD", "123456"))
    parser.add_argument("--db", default=os.environ.get("DB_NAME", "lease_db"))
    args = parser.parse_args()

    mysql_args = build_mysql_args(args.host, args.port, args.user, args.password)
    statements = build_cleanup_sql() + build_seed_sql()
    print(f"==> 为 {MOCK_OPENID} 叠加演示数据到 {args.db}（{len(statements)} 段 SQL）...")
    run_sql(mysql_args, args.db, "\n".join(statements))

    counts = subprocess.run(
        mysql_args + [args.db, "-N", "-e",
                      "SELECT CONCAT(table_name, '=', cnt) FROM ("
                      f"SELECT 'usr_users' table_name, COUNT(*) cnt FROM usr_users WHERE openid IN ('{MOCK_OPENID}','{EMPLOYEE_OPENID}') UNION ALL "
                      f"SELECT 'acct_accounts', COUNT(*) FROM acct_accounts WHERE user_id = {USER_ID} UNION ALL "
                      f"SELECT 'trd_balance_transactions', COUNT(*) FROM trd_balance_transactions WHERE user_id = {USER_ID} UNION ALL "
                      f"SELECT 'trd_recharge_records', COUNT(*) FROM trd_recharge_records WHERE user_id = {USER_ID} UNION ALL "
                      f"SELECT 'trd_payments', COUNT(*) FROM trd_payments WHERE user_id = {USER_ID} UNION ALL "
                      f"SELECT 'trd_refunds', COUNT(*) FROM trd_refunds WHERE user_id = {USER_ID} UNION ALL "
                      f"SELECT 'ord_orders', COUNT(*) FROM ord_orders WHERE user_id = {USER_ID} UNION ALL "
                      f"SELECT 'ord_order_items', COUNT(*) FROM ord_order_items WHERE order_id IN (SELECT id FROM ord_orders WHERE user_id = {USER_ID}) UNION ALL "
                      f"SELECT 'ord_meal_reservations', COUNT(*) FROM ord_meal_reservations WHERE user_id = {USER_ID} UNION ALL "
                      f"SELECT 'mtg_reservations', COUNT(*) FROM mtg_reservations WHERE user_id = {USER_ID} UNION ALL "
                      f"SELECT 'mkt_user_coupons', COUNT(*) FROM mkt_user_coupons WHERE user_id = {USER_ID} UNION ALL "
                      f"SELECT 'sys_notifications', COUNT(*) FROM sys_notifications WHERE user_id = {USER_ID} UNION ALL "
                      f"SELECT 'usr_member_purchases', COUNT(*) FROM usr_member_purchases WHERE enterprise_id = {ENTERPRISE_ID}) t ORDER BY 1;"],
        capture_output=True, text=True, check=True)
    print("==> 演示数据统计：")
    for line in counts.stdout.strip().splitlines():
        print("   ", line)
    print(f"==> 完成。开发登录用户 openid={MOCK_OPENID}（企业管理员 / VIP），员工 openid={EMPLOYEE_OPENID}")


if __name__ == "__main__":
    sys.exit(main())
