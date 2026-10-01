package com.example.leaseplatform.prd.service;

import com.example.leaseplatform.common.cache.CacheSpec;
import com.example.leaseplatform.common.cache.MultiLevelCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 商品缓存键与失效操作的<b>唯一出处</b>。
 *
 * <p>为什么单独抽一个组件，而不是各自在 Service 里拼键：
 * 商品缓存曾被写坏过一次 —— 读路径写 {@code product:detail:1}、失效路径写
 * {@code cache:product:detail:1}，键不匹配导致<b>失效静默不生效</b>：
 * 读写都正常、单测也过，但改了商品永远读到旧值，只有端到端才暴露。
 * 因此键的构造（{@link #detailKey}）与失效（{@link #evictDetail}）必须只有一份实现。
 *
 * <p><b>谁必须调用失效</b>（公开详情的 VO 里含 {@code skus[].stock} 与 {@code specGroups}，
 * 任何影响它们的写操作都要失效）：
 * <ul>
 *   <li>{@code PrdProductService} 的商品增删改、上下架 → 失效详情 + 推进列表缓存代；</li>
 *   <li>{@code PrdSkuService} 的 SKU 增删改/上下架、规格组增删改 → 失效详情。
 *       列表页不携带 SKU（{@code withSkuDetail} 只在详情路径调用），故无需推进列表缓存代。</li>
 * </ul>
 *
 * <p>注意：本项目库存<b>不由订单驱动</b>（订单流程不扣减库存），
 * 只有管理端改商品/SKU 才会改，所以这两个入口覆盖全了。
 */
@Component
@RequiredArgsConstructor
public class PrdProductCache {

    private final MultiLevelCache cache;

    /** 商品详情的逻辑键（不含全局前缀，交由 {@link MultiLevelCache#l2Key} 统一加） */
    public String detailKey(Long productId) {
        return CacheSpec.PRODUCT_DETAIL + ':' + productId;
    }

    /** 失效单个商品详情（事务提交后生效） */
    public void evictDetail(Long productId) {
        if (productId == null) {
            return;
        }
        cache.evictAfterCommit(CacheSpec.PRODUCT_DETAIL, MultiLevelCache.l2Key(detailKey(productId)));
    }

    /**
     * 让公开商品列表缓存整体失效（事务提交后生效）。
     *
     * <p>只推进一个「缓存代」计数器，不枚举分页键 —— 分页键会随
     * (筛选, 页码, 页大小) 组合变多，逐个删需要 {@code KEYS}/{@code SCAN}（生产禁用）。
     */
    public void evictList() {
        cache.bumpGenerationAfterCommit(CacheSpec.PRODUCT_LIST_GENERATION);
    }

    /** 当前列表缓存代；null 表示计数器不可用（调用方应跳过缓存直查） */
    public Long listGeneration() {
        return cache.generationOrNull(CacheSpec.PRODUCT_LIST_GENERATION);
    }
}
