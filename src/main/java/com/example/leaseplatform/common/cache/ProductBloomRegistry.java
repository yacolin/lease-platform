package com.example.leaseplatform.common.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.leaseplatform.prd.entity.PrdProduct;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品 ID 布隆过滤器注册表：为「按 ID 查商品详情」提供廉价的不存在判定。
 *
 * <p>为什么只有商品详情值得上布隆（docs 评估 §5.2）：布隆只在
 * 「按 ID 点查 + ID 空间稀疏 + 存在探测流量 + 表足够大」同时成立时有正收益。
 * 本项目其余按 ID 查的实体整表只有几行，整表 L1 缓存即可，布隆反而是负收益。
 *
 * <p><b>正确性依据</b>（docs 评估 §5.3）：本过滤器<b>只增不删</b>。
 * 之所以安全，是因为 {@code prd_products} 主键为雪花 ID 永不复用，
 * 且删除走逻辑删除（行永远物理存在）——因此不会出现「已加入的元素需要被移出」的情形，
 * 也就永不产生假阴性。
 *
 * <p>⚠️ <b>一条需要维护的不变量</b>：{@link #warmup()} 只装载
 * {@code is_deleted=0} 的商品。这对当前代码是安全的，因为本仓库<b>没有</b>
 * 「恢复已删除商品」的接口（{@code PrdProductService.delete} 是单向的）。
 * 若将来新增恢复功能，必须同时 {@link #add(long)} 该 ID，
 * 否则该商品会被本过滤器误判为「一定不存在」而返回 404。
 *
 * <p><b>未预热放行</b>（对应参考实现 products/cache.go 的 {@code count == 0 → return true}）：
 * 预热完成前 {@link #mayExist} 一律返回 {@code true}，否则启动瞬间会把全部商品判为不存在。
 *
 * <p><b>多实例窗口</b>：每个实例各持一份过滤器。实例 A 新建商品后，实例 B 要等
 * {@link #topUp()} 增量补齐（默认 30s）才会知道，此窗口内 B 可能把该商品判为不存在。
 * 当前 docker-compose 为单实例部署，此风险暂不触发；扩容前需知晓该窗口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductBloomRegistry {

    /** 预期商品数（超出后误判率上升，但不会出现假阴性；当前业务量远小于此） */
    private static final long EXPECTED_INSERTIONS = 100_000;

    /** 期望误判率 1% */
    private static final double FPP = 0.01;

    /** 增量补齐的单批上限，避免一次拉取过多 */
    private static final int TOP_UP_BATCH = 5_000;

    private final PrdProductMapper productMapper;

    private final LongBloomFilter filter = new LongBloomFilter(EXPECTED_INSERTIONS, FPP);

    /** 预热是否完成；未完成时一律放行 */
    private volatile boolean ready = false;

    /** 已装载的最大 ID，用于增量补齐 */
    private volatile long maxId = 0L;

    // ── 生命周期 ─────────────────────────────────────────────────────────────

    /** 启动预热：全量装载现有商品 ID */
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        try {
            List<Object> ids = productMapper.selectObjs(
                    new QueryWrapper<PrdProduct>().select("id"));
            long max = 0L;
            for (Object id : ids) {
                if (id instanceof Number n) {
                    long v = n.longValue();
                    filter.add(v);
                    max = Math.max(max, v);
                }
            }
            this.maxId = max;
            this.ready = true;
            log.info("商品布隆过滤器预热完成：{} 个 ID，m={} bit，k={}",
                    ids.size(), filter.bitSize(), filter.hashCount());
        } catch (Exception e) {
            // 预热失败不能阻止启动：ready 保持 false → 全部请求放行到 DB（安全降级）
            log.error("商品布隆过滤器预热失败，将放行全部请求（安全降级，不影响正确性）", e);
        }
    }

    /**
     * 增量补齐：把启动后新增的商品 ID 补进过滤器。
     *
     * <p>主要作用是收敛多实例窗口——本实例的 {@link #add} 只影响自己的过滤器，
     * 其它实例靠本方法在间隔内追上。
     */
    @Scheduled(
            initialDelayString = "${lease.cache.product-bloom.top-up-initial-delay-ms:30000}",
            fixedDelayString = "${lease.cache.product-bloom.top-up-interval-ms:30000}")
    public void topUp() {
        if (!ready) {
            return;
        }
        try {
            List<Object> ids = productMapper.selectObjs(new QueryWrapper<PrdProduct>()
                    .select("id")
                    .gt("id", maxId)
                    .orderByAsc("id")
                    .last("LIMIT " + TOP_UP_BATCH));
            long max = maxId;
            for (Object id : ids) {
                if (id instanceof Number n) {
                    long v = n.longValue();
                    filter.add(v);
                    max = Math.max(max, v);
                }
            }
            if (max > maxId) {
                this.maxId = max;
                log.debug("商品布隆过滤器增量补齐 {} 个 ID，maxId={}", ids.size(), max);
            }
        } catch (Exception e) {
            log.warn("商品布隆过滤器增量补齐失败（下轮重试）", e);
        }
    }

    // ── 查询 / 写入 ───────────────────────────────────────────────────────────

    /** @return {@code false} 表示该商品 ID <b>一定不存在</b>，可直接 404 且无需查库 */
    public boolean mayExist(long productId) {
        return !ready || filter.mightContain(productId);
    }

    /** 新增商品时加入（只增不删） */
    public void add(long productId) {
        if (productId > 0) {
            filter.add(productId);
        }
    }

    /** 供测试与健康检查：预热是否完成 */
    public boolean isReady() {
        return ready;
    }
}
