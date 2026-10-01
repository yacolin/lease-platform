package com.example.leaseplatform.common.cache;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 布隆过滤器单测：重点验证两件事——<b>永无假阴性</b>、<b>误判率符合配置</b>。
 */
class LongBloomFilterTest {

    @Test
    void addedKeys_shouldNeverBeReportedAbsent() {
        LongBloomFilter filter = new LongBloomFilter(10_000, 0.01);

        for (long i = 1; i <= 10_000; i++) {
            filter.add(i);
        }

        // 假阴性是布隆过滤器的致命错误（会把存在的商品误判为不存在 → 用户看到 404）
        for (long i = 1; i <= 10_000; i++) {
            assertThat(filter.mightContain(i)).as("已加入的 %d 不得被判定为不存在", i).isTrue();
        }
    }

    @Test
    void falsePositiveRate_shouldBeWithinConfiguredBound() {
        int n = 50_000;
        double p = 0.01;
        LongBloomFilter filter = new LongBloomFilter(n, p);
        for (long i = 1; i <= n; i++) {
            filter.add(i);
        }

        // 用确定不在集合中的键探测误判率
        int probes = 200_000;
        int falsePositives = 0;
        for (long i = 1; i <= probes; i++) {
            if (filter.mightContain(1_000_000_000L + i)) {
                falsePositives++;
            }
        }
        double fpr = (double) falsePositives / probes;

        // 理论值 p=1%，留 3 倍余量避免偶发抖动
        assertThat(fpr).as("实测误判率 %.4f 应不超过配置值 %.4f 的 3 倍", fpr, p)
                .isLessThan(p * 3);
    }

    @Test
    void snowflakeLikeSequentialIds_shouldStillSpreadEvenly() {
        // 雪花 ID 高位相近（同一毫秒内连续），若不做混合会导致位分布极不均匀、误判率飙升
        long base = 1_900_000_000_000_000_000L;
        LongBloomFilter filter = new LongBloomFilter(5_000, 0.01);
        for (long i = 0; i < 5_000; i++) {
            filter.add(base + i);
        }

        int probes = 20_000;
        int falsePositives = 0;
        for (long i = 0; i < probes; i++) {
            if (filter.mightContain(base + 1_000_000L + i)) {
                falsePositives++;
            }
        }
        assertThat((double) falsePositives / probes).isLessThan(0.03);
        // 位数组未被占满，说明分布是散的而不是挤在少数位
        assertThat(filter.cardinality()).isLessThan(filter.bitSize());
    }

    @Test
    void concurrentAddAndQuery_shouldNotLoseInsertedKeys() throws Exception {
        LongBloomFilter filter = new LongBloomFilter(20_000, 0.01);
        int threads = 8;
        int perThread = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = new ArrayList<>();

        try {
            for (int t = 0; t < threads; t++) {
                final int offset = t * perThread;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            filter.add(offset + i + 1L);
                        }
                    } catch (Throwable e) {
                        synchronized (errors) {
                            errors.add(e);
                        }
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(errors).isEmpty();
        // 并发写入后依然不得出现假阴性
        for (long i = 1; i <= (long) threads * perThread; i++) {
            assertThat(filter.mightContain(i)).as("并发加入的 %d 不得丢失", i).isTrue();
        }
    }

    @Test
    void emptyFilter_shouldReportEverythingAbsent() {
        LongBloomFilter filter = new LongBloomFilter(1_000, 0.01);

        AtomicInteger present = new AtomicInteger();
        for (long i = 1; i <= 1_000; i++) {
            if (filter.mightContain(i)) {
                present.incrementAndGet();
            }
        }
        assertThat(present).as("空过滤器不应报告任何元素存在（无假阳性基础）").hasValue(0);
    }

    @Test
    void invalidArguments_shouldThrow() {
        assertThatThrownBy(() -> new LongBloomFilter(0, 0.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LongBloomFilter(1_000, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LongBloomFilter(1_000, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parameters_shouldBeSizedAsExpected() {
        // n=10000, p=0.01 → m≈95851 bit, k≈7
        LongBloomFilter filter = new LongBloomFilter(10_000, 0.01);
        assertThat(filter.bitSize()).isBetween(95_000, 96_500);
        assertThat(filter.hashCount()).isEqualTo(7);
    }
}
