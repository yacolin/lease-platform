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

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 逻辑缓存名 → L1 实例（每个逻辑缓存独立的容量与 TTL 策略） */
    private final Map<String, Cache<String, Object>> l1Caches = new ConcurrentHashMap<>();

    // ── 读 ───────────────────────────────────────────────────────────────────

    /** 列表缓存读：见类注释的读路径 */
    public <T> List<T> getList(String cacheName, String l2Key, Class<T> elementType,
                               Duration l2Ttl, Supplier<List<T>> loader) {
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return get(cacheName, l2Key, type, l2Ttl, loader);
    }

    /** 单值缓存读：见类注释的读路径 */
    public <T> T get(String cacheName, String l2Key, Class<T> type,
                     Duration l2Ttl, Supplier<T> loader) {
        return get(cacheName, l2Key, objectMapper.getTypeFactory().constructType(type), l2Ttl, loader);
    }

    private <T> T get(String cacheName, String l2Key, JavaType type,
                      Duration l2Ttl, Supplier<T> loader) {
        Cache<String, Object> l1 = l1(cacheName);
        Object hit = l1.getIfPresent(l2Key);
        if (hit != null) {
            return cast(hit);
        }
        // Caffeine 的 get(key, fn) 对同一 key 是互斥的：并发只放一个线程进来
        Object value = l1.get(l2Key, key -> {
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
        try {
            String json = redisTemplate.opsForValue().get(l2Key);
            return json == null ? null : objectMapper.readValue(json, type);
        } catch (Exception e) {
            // 缓存读失败不应影响业务：降级为穿透到 DB
            log.warn("L2 缓存读取失败，降级直查数据库: key={}", l2Key, e);
            return null;
        }
    }

    private void writeL2(String l2Key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(l2Key, objectMapper.writeValueAsString(value), ttl);
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
    public void evict(String cacheName, String l2Key) {
        l1(cacheName).invalidate(l2Key);
        try {
            redisTemplate.delete(l2Key);
            redisTemplate.convertAndSend(EVICT_CHANNEL, l2Key);
        } catch (Exception e) {
            log.warn("L2 缓存失效失败（L1 已清，最迟 L2 TTL 到期后自愈）: key={}", l2Key, e);
        }
    }

    /** 仅清本实例 L1（供 Pub/Sub 订阅者调用） */
    void evictLocal(String l2Key) {
        l1Caches.values().forEach(c -> c.invalidate(l2Key));
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

    // ── 内部 ─────────────────────────────────────────────────────────────────

    private Cache<String, Object> l1(String cacheName) {
        return l1Caches.computeIfAbsent(cacheName, n -> Caffeine.newBuilder()
                .maximumSize(L1_MAX_SIZE)
                .expireAfter(new Expiry<String, Object>() {
                    @Override
                    public long expireAfterCreate(String key, Object value, long currentTime) {
                        return jitteredNanos(CacheSpec.L1_TTL);
                    }

                    @Override
                    public long expireAfterUpdate(String key, Object value, long currentTime,
                                                  long currentDuration) {
                        return jitteredNanos(CacheSpec.L1_TTL);
                    }

                    @Override
                    public long expireAfterRead(String key, Object value, long currentTime,
                                                long currentDuration) {
                        return currentDuration; // 读不续期
                    }
                })
                .build());
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
}
