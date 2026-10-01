package com.example.leaseplatform.common.cache;

import java.time.Duration;

/**
 * 缓存规格：一个「逻辑缓存」的名称与两级 TTL。
 *
 * <p>约定：每个逻辑缓存对应 <b>一个</b> L2 Redis key（整表/整列表缓存），
 * 因此失效时只需精确 {@code DEL} 该 key，无需 {@code KEYS}/{@code SCAN} 模式扫描
 * （参考实现 products/cache.go 用 {@code KEYS} 扫前缀，是必须避免的反面做法）。
 *
 * <p>TTL 设计依据见 docs/缓存与查询效率评估.md §4：
 * 这些参照数据（分类/会议室/充值档位/会员等级/优惠券模板）整表只有 3~92 行、写频率极低，
 * 适合做进程内 L1 整表常驻。
 */
public final class CacheSpec {

    // ── 逻辑缓存名称（同时作为 Pub/Sub 广播的标识）───────────────────────────

    /** 商品分类公开列表（整表） */
    public static final String CATEGORY_LIST = "category:list";
    /** 会议室公开列表（整表） */
    public static final String ROOM_LIST = "room:list";
    /** 充值档位公开列表（整表） */
    public static final String TIER_LIST = "tier:list";
    /** 会员等级公开列表（整表） */
    public static final String MEMBER_LEVEL_LIST = "member-level:list";
    /** 可领取优惠券公开列表（整表） */
    public static final String COUPON_LIST = "coupon:list";
    /** 每日菜单公开列表（按日期分片：逻辑缓存名为本值，L2 key 追加 :yyyy-MM-dd） */
    public static final String MENU_LIST = "menu:list";
    /** 商品详情（按 ID） */
    public static final String PRODUCT_DETAIL = "product:detail";

    // ── TTL ──────────────────────────────────────────────────────────────────

    /** L1（进程内）TTL：短于 L2，降低多实例下脏读窗口；并带 ±20% 抖动防雪崩 */
    public static final Duration L1_TTL = Duration.ofMinutes(2);
    /** L2（Redis）TTL：参照数据写频率极低，可放长 */
    public static final Duration L2_TTL = Duration.ofMinutes(30);
    /** 菜单按日期缓存：L2 保留到当日结束后一段时间即可 */
    public static final Duration MENU_L2_TTL = Duration.ofMinutes(30);

    /** 商品详情 L2 TTL */
    public static final Duration PRODUCT_L2_TTL = Duration.ofMinutes(10);

    /** 商品「不可见」空值缓存 TTL：短，给重新上架留出较短的纠错窗口 */
    public static final Duration PRODUCT_NULL_TTL = Duration.ofSeconds(60);

    private CacheSpec() {
    }
}
