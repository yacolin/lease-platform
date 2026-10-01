package com.example.leaseplatform.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多级缓存门面单测：覆盖 L1→L2→loader 的读取顺序、回填、失效与空值处理。
 */
@ExtendWith(MockitoExtension.class)
class MultiLevelCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private MultiLevelCache cache;

    @BeforeEach
    void setUp() {
        cache = new MultiLevelCache(redisTemplate, new ObjectMapper());
    }

    @Test
    void miss_shouldLoadFromLoader_andWriteL2() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        List<String> result = cache.getList("c1", "cache:c1", String.class, Duration.ofMinutes(5),
                () -> List.of("a", "b"));

        assertThat(result).containsExactly("a", "b");
        verify(valueOps).set(eq("cache:c1"), anyString(), any(Duration.class));
    }

    @Test
    void secondRead_shouldHitL1_andNotCallLoaderAgain() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();

        cache.getList("c2", "cache:c2", String.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return List.of("a"); });
        List<String> second = cache.getList("c2", "cache:c2", String.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return List.of("a"); });

        assertThat(second).containsExactly("a");
        assertThat(loads).as("第二次读命中 L1，不应再穿透").hasValue(1);
        verify(valueOps).get("cache:c2"); // L2 只被读了一次
    }

    @Test
    void l2Hit_shouldNotCallLoader_andBackfillL1() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("cache:c3")).thenReturn("[\"x\"]");
        AtomicInteger loads = new AtomicInteger();

        List<String> first = cache.getList("c3", "cache:c3", String.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return List.of("db"); });
        List<String> second = cache.getList("c3", "cache:c3", String.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return List.of("db"); });

        assertThat(first).containsExactly("x");
        assertThat(second).containsExactly("x");
        assertThat(loads).as("L2 命中后不应查库").hasValue(0);
    }

    @Test
    void loaderReturningNull_shouldNotBeCached_soNextReadRetries() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();

        cache.getList("c4", "cache:c4", String.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return null; });
        cache.getList("c4", "cache:c4", String.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return null; });

        assertThat(loads).as("null 不入缓存，下次仍应重试").hasValue(2);
        verify(valueOps, never()).set(eq("cache:c4"), anyString(), any(Duration.class));
    }

    @Test
    void redisFailure_shouldDegradeToLoader() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));

        List<String> result = cache.getList("c5", "cache:c5", String.class, Duration.ofMinutes(5),
                () -> List.of("fallback"));

        assertThat(result).as("缓存不可用时应降级直查，而不是抛错").containsExactly("fallback");
    }

    @Test
    void evict_shouldClearL1AndL2_andBroadcast() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();

        cache.getList("c6", "cache:c6", String.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return List.of("v1"); });

        cache.evict("c6", "cache:c6");

        verify(redisTemplate).delete("cache:c6");
        verify(redisTemplate).convertAndSend(MultiLevelCache.EVICT_CHANNEL, "cache:c6");

        // L1 已清 + L2 已删 → 再读必须重新查库，且拿到的是新值
        List<String> after = cache.getList("c6", "cache:c6", String.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return List.of("v2"); });
        assertThat(loads).as("失效后应重新加载").hasValue(2);
        assertThat(after).containsExactly("v2");
    }

    @Test
    void evictAfterCommit_withoutTransaction_shouldEvictImmediately() {
        // 无事务上下文时应立即失效（事务内的 afterCommit 路径另由事务测试覆盖）
        cache.evictAfterCommit("c7", "cache:c7");

        verify(redisTemplate).delete("cache:c7");
        verify(redisTemplate).convertAndSend(MultiLevelCache.EVICT_CHANNEL, "cache:c7");
    }

    @Test
    void l2Key_shouldBeNamespaced() {
        assertThat(MultiLevelCache.l2Key("category:list")).isEqualTo("cache:category:list");
    }
}
