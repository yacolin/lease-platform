package com.example.leaseplatform.common.cache;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 针对 {@code long} 主键的布隆过滤器（无第三方依赖，线程安全）。
 *
 * <p>语义：{@link #mightContain} 返回 {@code false} 表示<b>一定不存在</b>；
 * 返回 {@code true} 表示<b>可能存在</b>（有配置内的误判率）。
 * 因此它只能用来「快速否定」，不能用来确认存在。
 *
 * <p>与参考实现的差异：
 * <ul>
 *   <li>参考实现用 {@code github.com/bits-and-blooms/bloom/v3}，其 {@code BloomFilter}
 *       <b>非并发安全</b>，需外部 {@code sync.RWMutex} 保护（products/cache.go:118）；
 *       本实现内部用 {@link AtomicLongArray}，读写在 bit 级别原子，
 *       <b>无需外部加锁</b>（误判率在并发写入下只有极轻微影响，可接受）。</li>
 *   <li>用 Kirsch-Mitzenmacher 双哈希法：由一次 64 位混合得到两个独立哈希
 *       {@code h1,h2}，再以 {@code (h1 + i*h2) mod m} 生成 k 个位下标，
 *       避免做 k 次完整哈希。</li>
 * </ul>
 *
 * <p><b>不支持删除</b>——这是布隆过滤器的固有性质（无法安全清零位，会影响其它元素的判定）。
 * 本项目恰好不受影响：主键是雪花 ID 永不复用，且目标实体（prd_products）走逻辑删除，
 * 行永远物理存在，故「只增不删」永不产生假阴性（见 docs 评估 §5.3）。
 */
public final class LongBloomFilter {

    /** 位数组，按 64 位字打包 */
    private final AtomicLongArray bits;

    /** 位数组长度 m */
    private final int numBits;

    /** 哈希个数 k */
    private final int numHashes;

    /**
     * 按预期元素数与误判率构造。
     *
     * @param expectedInsertions 预期元素数 n（超出后误判率会上升，但不会出现假阴性）
     * @param fpp                期望误判率 p（如 0.01）
     */
    public LongBloomFilter(long expectedInsertions, double fpp) {
        if (expectedInsertions <= 0) {
            throw new IllegalArgumentException("expectedInsertions 必须为正数");
        }
        if (!(fpp > 0 && fpp < 1)) {
            throw new IllegalArgumentException("fpp 必须在 (0,1) 开区间内");
        }
        // m = -n*ln(p) / (ln2)^2
        long m = (long) Math.ceil(-expectedInsertions * Math.log(fpp) / (Math.log(2) * Math.log(2)));
        // k = (m/n)*ln2
        int k = Math.max(1, (int) Math.round((double) m / expectedInsertions * Math.log(2)));

        this.numBits = (int) Math.max(64, Math.min(m, Integer.MAX_VALUE - 64L));
        this.numHashes = k;
        this.bits = new AtomicLongArray((numBits + 63) >>> 6);
    }

    /** 加入一个元素 */
    public void add(long key) {
        long h1 = mix64(key);
        long h2 = mix64(key + 0x9E3779B97F4A7C15L);
        for (int i = 0; i < numHashes; i++) {
            setBit(indexOf(h1, h2, i));
        }
    }

    /** @return {@code false} 表示一定不存在；{@code true} 表示可能存在 */
    public boolean mightContain(long key) {
        long h1 = mix64(key);
        long h2 = mix64(key + 0x9E3779B97F4A7C15L);
        for (int i = 0; i < numHashes; i++) {
            if (!getBit(indexOf(h1, h2, i))) {
                return false;
            }
        }
        return true;
    }

    /** 已分配的位数（m），供测试与日志使用 */
    public int bitSize() {
        return numBits;
    }

    /** 哈希个数（k） */
    public int hashCount() {
        return numHashes;
    }

    /** 已置位的 bit 数，用于估算实际装载率 */
    public long cardinality() {
        long set = 0;
        for (int i = 0; i < bits.length(); i++) {
            set += Long.bitCount(bits.get(i));
        }
        return set;
    }

    private int indexOf(long h1, long h2, int i) {
        long combined = h1 + (long) i * h2;
        // floorMod 保证非负
        return (int) Math.floorMod(combined, numBits);
    }

    private void setBit(int index) {
        int word = index >>> 6;
        long mask = 1L << (index & 63);
        bits.getAndUpdate(word, v -> v | mask);
    }

    private boolean getBit(int index) {
        int word = index >>> 6;
        long mask = 1L << (index & 63);
        return (bits.get(word) & mask) != 0;
    }

    /** splitmix64 终混合：把顺序 ID 打散成均匀分布（雪花 ID 高位相近，必须混合） */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
