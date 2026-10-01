package com.example.leaseplatform.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 两级缓存门面：L1 Caffeine（进程内）+ L2 Redis（跨实例）。
 *
 * <p>读路径（对应参考实现 {@code products/repo_products.go:39-67} 的 L1 → Bloom → L2 → DB）：
 * <pre>
 *   L1 命中 → 返回
 *  L2 命中 → 回填 L1 → 返回
 *  未命中  → loader（查库）→ 回填 L2 + L1 → 返回
 * </pre>
 *
 * <p>三个关键机制，均来自参考实现：
 * <ol>
 *   <li><b>TTL 抖动</b>（{@code jitteredTTL}）：每个条目独立随机 ±20%，
 *       避免同批 key 同时失效造成缓存雪崩；</li>
 *   <li><b>单飞（single-flight）</b>：用 Caffeine 的 {@code get(key, mappingFunction)}，
 *       同一 key 只允许一个线程穿透到 L2/DB，其余等待 —— 等价 Go 侧的
 *       {@code singleflight.Group} 与 brands/cache.go 的 mutex+channel 重建；</li>
 *   <li><b>多实例 L1 失效</b>：{@link #evict} 在清本地 L1 的同时发布 Redis Pub/Sub 消息，
 *       由 {@link CacheEvictSubscriber} 清其它实例的 L1 —— 否则实例 A 更新后
 *       实例 B 的 L1 最多脏一个 L1 TTL。</li>
 * </ol>
 *
 * <p><b>不做的事</b>：不使用 {@code KEYS}/{@code SCAN}。每个逻辑缓存只对应一个 L2 key，
 * 失效是精确 {@code DEL}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiLevelCache {

    /** 单个逻辑缓存的 L1 条目上限（参照数据整表通常只有几十行） */
    private static final int L1_MAX_SIZE = 4096;

    /** L1 TTL 抖动比例：±20% */
    private static final double TTL_JITTER = 0.2;

    /** Redis key 统一前缀，避免与认证会话（auth:refresh:*）等混在一起 */
    public static final String KEY_PREFIX = "cache:";

    /** L1 失效广播频道 */
    public static final String EVICT_CHANNEL = "cache:evict";

    /** 空值哨兵在 L2 中的字面量（JSON 字符串字面量，与正常值不会冲突） */
    private static final String NULL_MARKER = "\"__CACHE_NULL__\"";

    /** 空值哨兵在 L1 中的单例标记 */
    private static final Object NULL_SENTINEL = new Object();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 逻辑缓存名 → L1 实例（每个逻辑缓存独立的容量与 TTL 策略） */
    private final Map<String, Cache<String, Object>> l1Caches = new ConcurrentHashMap<>();

    // ── 读 ───────────────────────────────────────────────────────────────────

    /** 列表缓存读：见类注释的读路径 */
    public <T> List<T> getList(String cacheName, String l2Key, Class<T> elementType,
                               Duration l2Ttl, Supplier<List<T>> loader) {
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return getByType(cacheName, l2Key, type, l2Ttl, loader);
    }

    /** 单值缓存读：见类注释的读路径 */
    public <T> T get(String cacheName, String l2Key, Class<T> type,
                     Duration l2Ttl, Supplier<T> loader) {
        return getByType(cacheName, l2Key, objectMapper.getTypeFactory().constructType(type), l2Ttl, loader);
    }

    /**
     * 单值缓存读，<b>并缓存 null</b>（空值哨兵 / negative cache）。
     *
     * <p>用途：缓存「确认不可见」的结果（如商品已下架 / 已逻辑删除的详情 404），
     * 避免同一批不存在的 ID 反复打穿到 DB（缓存穿透）。
     * null 用较短的 {@code nullTtl} 缓存，给数据恢复留出较短的纠错窗口。
     *
     * <p>注意 L1 中的哨兵条目仍按 {@link CacheSpec#L1_TTL} 过期（Caffeine 的 Expiry
     * 对本门面统一使用 L1_TTL），仅 L2 使用 {@code nullTtl}；两者都远短于正常值的 TTL 语义。
     */
    public <T> T getOrNull(String cacheName, String rawKey, Class<T> type,
                           Duration l2Ttl, Duration nullTtl, Supplier<T> loader) {
        // 必须是 final 才能被下方的 lambda 捕获
        final String l2Key = normalize(rawKey);
        Cache<String, Object> l1 = l1(cacheName, effectiveL1Ttl(l2Ttl));
        Object hit = l1.getIfPresent(l2Key);
        if (hit != null) {
            return hit == NULL_SENTINEL ? null : cast(hit);
        }
        Object value = l1.get(l2Key, k -> {
            String json = readL2Raw(l2Key);
            if (json != null) {
                if (NULL_MARKER.equals(json)) {
                    return NULL_SENTINEL;
                }
                try {
                    return objectMapper.readValue(json, objectMapper.getTypeFactory().constructType(type));
                } catch (Exception e) {
                    log.warn("L2 缓存反序列化失败，降级直查数据库: key={}", l2Key, e);
                }
            }
            T loaded = loader.get();
            if (loaded == null) {
                writeL2Raw(l2Key, NULL_MARKER, nullTtl);
                return NULL_SENTINEL;
            }
            writeL2(l2Key, loaded, l2Ttl);
            return loaded;
        });
        return value == NULL_SENTINEL ? null : cast(value);
    }

    /**
     * 泛型读（如 {@code PageResult<ProductVO>}）：由调用方给出 {@link JavaType}。
     * 见类注释的读路径。
     *
     * <p>刻意不叫 {@code get} 重载：与 {@code get(..., Class<T>, ...)} 仅第三参不同，
     * 保留同名会让所有 {@code any()} 形式的 Mockito 匹配产生歧义（已踩到过）。
     */
    public <T> T getTyped(String cacheName, String l2Key, JavaType type,
                          Duration l2Ttl, Supplier<T> loader) {
        return getByType(cacheName, l2Key, type, l2Ttl, loader);
    }

    private <T> T getByType(String cacheName, String rawKey, JavaType type,
                            Duration l2Ttl, Supplier<T> loader) {
        final String l2Key = normalize(rawKey);
        Cache<String, Object> l1 = l1(cacheName, effectiveL1Ttl(l2Ttl));
        Object hit = l1.getIfPresent(l2Key);
        if (hit != null) {
            return cast(hit);
        }
        // Caffeine 的 get(key, fn) 对同一 key 是互斥的：并发只放一个线程进来
        Object value = l1.get(l2Key, k -> {
            T fromL2 = readL2(l2Key, type);
            if (fromL2 != null) {
                return fromL2;
            }
            T loaded = loader.get();
            if (loaded != null) {
                writeL2(l2Key, loaded, l2Ttl);
            }
            return loaded;
        });
        return cast(value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    private <T> T readL2(String l2Key, JavaType type) {
        String json = readL2Raw(l2Key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("L2 缓存反序列化失败，降级直查数据库: key={}", l2Key, e);
            return null;
        }
    }

    /** 读 L2 原始字符串；任何异常都降级为「未命中」 */
    private String readL2Raw(String l2Key) {
        try {
            return redisTemplate.opsForValue().get(l2Key);
        } catch (Exception e) {
            // 缓存读失败不应影响业务：降级为穿透到 DB
            log.warn("L2 缓存读取失败，降级直查数据库: key={}", l2Key, e);
            return null;
        }
    }

    private void writeL2(String l2Key, Object value, Duration ttl) {
        try {
            writeL2Raw(l2Key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("L2 缓存写入失败（不影响本次返回）: key={}", l2Key, e);
        }
    }

    private void writeL2Raw(String l2Key, String payload, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(l2Key, payload, ttl);
        } catch (Exception e) {
            log.warn("L2 缓存写入失败（不影响本次返回）: key={}", l2Key, e);
        }
    }

    // ── 失效 ─────────────────────────────────────────────────────────────────

    /**
     * 失效一个逻辑缓存：清本地 L1 + 删 L2 + 广播清其它实例的 L1。
     *
     * <p>应在写操作<b>提交后</b>调用（见调用方对 {@code afterCommit} 的处理），
     * 否则事务回滚会留下已被清空但实际未变更的缓存（无正确性问题，只是白白失效一次）。
     */
    public void evict(String cacheName, String rawKey) {
        final String l2Key = normalize(rawKey);
        // 只清已存在的 L1：不在此处创建，避免用默认 TTL 抢先创建后与读路径的 TTL 不一致
        Cache<String, Object> existing = l1Caches.get(cacheName);
        if (existing != null) {
            existing.invalidate(l2Key);
        }
        try {
            redisTemplate.delete(l2Key);
            redisTemplate.convertAndSend(EVICT_CHANNEL, l2Key);
        } catch (Exception e) {
            log.warn("L2 缓存失效失败（L1 已清，最迟 L2 TTL 到期后自愈）: key={}", l2Key, e);
        }
    }

    /** 仅清本实例 L1（供 Pub/Sub 订阅者调用） */
    void evictLocal(String l2Key) {
        String key = normalize(l2Key);
        l1Caches.values().forEach(c -> c.invalidate(key));
    }

    /**
     * <b>事务提交后</b>再失效；当前无事务时立即失效。
     *
     * <p>为什么不能在事务内直接失效：若在提交前清缓存，并发读可能在事务提交前
     * 读到<b>旧值</b>并把它写回缓存，于是缓存里留下一个「旧值 + 新 TTL」的脏条目，
     * 要等整个 TTL 才自愈。放到 {@code afterCommit} 可避免这个窗口。
     *
     * <p>（提交前清、提交后清都只是「多失效一次」的差别，但提交前清会引入上述脏读回填。）
     */
    public void evictAfterCommit(String cacheName, String l2Key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(cacheName, l2Key);
                }
            });
        } else {
            evict(cacheName, l2Key);
        }
    }

    // ── 缓存代（generation）──────────────────────────────────────────────────

    /**
     * 读取「缓存代」。
     *
     * <p><b>键不存在时用「当前时间戳」初始化（SET NX），而不是回退到 0</b> ——
     * 这一点是修一个真实脏读 bug 得到的教训：
     * 计数器被 Redis 淘汰（maxmemory）/ 丢失 / 进程重启后，若回退到 0，
     * 就会重新命中上一轮遗留的 {@code v0} 分页键，读到过期数据
     * （实测复现：真实 total 已是 3866，接口却返回 3867）。
     * 根因是「0」本身是一个<b>历史上真实用过的代</b>。
     * 用时间戳初始化可保证新代一定大于历史任何一代，旧键立即不可达。
     *
     * @return 当前缓存代；<b>返回 null 表示计数器不可用</b>，
     *         调用方应当跳过缓存直查（安全降级，而不是拿一个可能撞代的数字去查缓存）
     */
    public Long generationOrNull(String name) {
        String key = KEY_PREFIX + name;
        try {
            String v = redisTemplate.opsForValue().get(key);
            if (v != null) {
                return Long.parseLong(v);
            }
            long seeded = System.currentTimeMillis();
            redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(seeded));
            String after = redisTemplate.opsForValue().get(key);
            return after == null ? seeded : Long.parseLong(after);
        } catch (Exception e) {
            log.warn("缓存代不可用，跳过缓存直查: {}", name, e);
            return null;
        }
    }

    /**
     * 推进「缓存代」（写入方调用）。语义等同让该族缓存整体失效。
     *
     * <p>先 {@code SET NX <时间戳>} 再 {@code INCR}：若键已缺失就直接 INCR 会得到 1，
     * 可能与历史上真实出现过的 {@code v1} 撞代 —— 同 {@link #generationOrNull} 的理由。
     */
    public void bumpGeneration(String name) {
        String key = KEY_PREFIX + name;
        try {
            redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(System.currentTimeMillis()));
            redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.warn("推进缓存代失败（该族缓存最迟 TTL 后自愈）: {}", name, e);
        }
    }

    /** 事务提交后再推进「缓存代」；无事务时立即推进 */
    public void bumpGenerationAfterCommit(String name) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bumpGeneration(name);
                }
            });
        } else {
            bumpGeneration(name);
        }
    }

    // ── 内部 ─────────────────────────────────────────────────────────────────

    /**
     * 取得（或惰性创建）某个逻辑缓存的 L1 实例。
     *
     * <p>TTL 在创建时绑定：同一 cacheName 的 L1 TTL 必须稳定，故调用方一律用
     * {@link #effectiveL1Ttl(Duration)} 由 L2 TTL 推导，而不是各自传值。
     */
    private Cache<String, Object> l1(String cacheName, Duration l1Ttl) {
        return l1Caches.computeIfAbsent(cacheName, n -> Caffeine.newBuilder()
                .maximumSize(L1_MAX_SIZE)
                .expireAfter(new Expiry<String, Object>() {
                    @Override
                    public long expireAfterCreate(String key, Object value, long currentTime) {
                        return jitteredNanos(l1Ttl);
                    }

                    @Override
                    public long expireAfterUpdate(String key, Object value, long currentTime,
                                                  long currentDuration) {
                        return jitteredNanos(l1Ttl);
                    }

                    @Override
                    public long expireAfterRead(String key, Object value, long currentTime,
                                                long currentDuration) {
                        return currentDuration; // 读不续期
                    }
                })
                .build());
    }

    /**
     * L1 TTL = min(全局 L1 上限, L2 TTL)。
     *
     * <p>目的：短 TTL 的缓存（如 30s 的订单统计）不应在进程内活得更久，
     * 否则「L2 已过期、L1 还留着」会让实际陈旧时间远超预期。
     */
    private static Duration effectiveL1Ttl(Duration l2Ttl) {
        return l2Ttl.compareTo(CacheSpec.L1_TTL) < 0 ? l2Ttl : CacheSpec.L1_TTL;
    }

    /** 对应参考实现的 jitteredTTL：base ± 20% */
    private static long jitteredNanos(Duration base) {
        long nanos = base.toNanos();
        long delta = (long) (nanos * TTL_JITTER);
        return nanos + ThreadLocalRandom.current().nextLong(-delta, delta + 1);
    }

    /** L2 Redis key：{@code cache:} + 名称（名称本身可含日期等后缀） */
    public static String l2Key(String name) {
        return KEY_PREFIX + name;
    }

    /**
     * 统一补全 L2 key 前缀的<b>安全网</b>。
     *
     * <p>存在的原因：读路径与失效路径若一处写了前缀、另一处漏写，缓存仍能正常工作
     * （能读能写），但<b>失效会静默失败</b>——留下一份永远不更新的脏缓存。
     * 这类 bug 不报错、测试也可能通过，只会在生产上表现为「改了数据但页面不变」。
     * （开发过程中确实发生过一次：读用了裸 key、失效用了带前缀 key。）
     *
     * <p>故此处对两种写法都接受：未带前缀的自动补上，已带前缀的原样返回，
     * 使读与失效必然落在同一个 key 上。
     */
    static String normalize(String l2Key) {
        return l2Key != null && l2Key.startsWith(KEY_PREFIX) ? l2Key : KEY_PREFIX + l2Key;
    }
}
