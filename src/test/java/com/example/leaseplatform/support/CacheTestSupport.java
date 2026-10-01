package com.example.leaseplatform.support;

import com.example.leaseplatform.common.cache.MultiLevelCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

/**
 * 集成测试的缓存清理工具。
 *
 * <p>为什么需要：集成测试跑在 {@code @Transactional} 里并以回滚结束，
 * 因此 {@code MultiLevelCache.evictAfterCommit} 注册的失效回调<b>永远不会触发</b>
 * （这是正确的生产语义：事务回滚说明数据没变，缓存仍有效）。
 * 但测试会在同一个事务内「先写后读」，读路径会把<b>未提交</b>的数据写进缓存，
 * 回滚后该缓存就成了脏数据，可能污染同一轮或下一轮测试。
 *
 * <p>故在每个集成测试开始前清空本项目的缓存命名空间，让用例自洽、不受执行顺序影响。
 *
 * <p>实现用 {@code KEYS} + {@code DEL}：仅用于测试（键空间只有个位数），
 * 生产代码中禁止使用 {@code KEYS}（见 docs 评估 §3.3），缓存失效一律走精确 DEL。
 */
public final class CacheTestSupport {

    public static void flushCacheNamespace(StringRedisTemplate redisTemplate) {
        Set<String> keys = redisTemplate.keys(MultiLevelCache.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private CacheTestSupport() {
    }
}
