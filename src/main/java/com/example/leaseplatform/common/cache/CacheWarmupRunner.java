package com.example.leaseplatform.common.cache;

import com.example.leaseplatform.mkt.service.MktCouponService;
import com.example.leaseplatform.mtg.service.RoomService;
import com.example.leaseplatform.prd.service.PrdCategoryService;
import com.example.leaseplatform.trd.service.RechargeTierService;
import com.example.leaseplatform.usr.service.MemberPurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * 启动预热：把「整表型参照数据」缓存一次性填好。
 *
 * <p><b>为什么需要预热</b>（原先缺这一步，导致启动后 Redis 里什么都看不到）：
 * <ol>
 *   <li><b>可验证性</b>：参照数据缓存是<b>读时懒加载</b>的，应用刚启动、还没有流量时
 *       Redis 里就是空的，很容易让人以为「缓存没生效」；</li>
 *   <li><b>首请求不再付冷启动代价</b>：预热前，上线后第一个打到某接口的请求仍要查库
 *       （实测空缓存时公开商品列表 106ms、分类 31ms，命中后分别降到 17ms / 3ms）；</li>
 *   <li><b>与参考实现一致</b>：gf-eshop 的 brands/products cache 都有 {@code Warmup()}，
 *       本项目评估报告 §6.2 也列了 {@code CacheWarmupRunner}。</li>
 * </ol>
 *
 * <p><b>只预热整表型缓存</b>（分类 / 会议室 / 充值档位 / 会员等级 / 优惠券）。
 * 其余缓存刻意不预热，原因各不相同：
 * <ul>
 *   <li>{@code menu:list:{date}}：按日期分片的键空间，预热「今天」只在当天有效且会翻滚；</li>
 *   <li>{@code product:detail:{id}}：按 ID 的键空间，预热等于把整张商品表复制进 Redis，
 *       而「不存在的 ID」已由布隆过滤器拦住，详情本身读时懒加载即可；</li>
 *   <li>{@code order:stats}：TTL 只有 30s，启动时预热大概率在首个请求到来前就过期了。</li>
 * </ul>
 *
 * <p><b>失败不影响启动</b>：逐项 try/catch，任一预热失败只记 WARN 并回退为「读时懒加载」——
 * 缓存是可选加速层，不该成为启动的单点。
 *
 * <p>用 {@code @Order(10)} 排在 {@link com.example.leaseplatform.config.FlywayMigrationRunner}
 * （{@code @Order(0)}）之后：生产首次启动必须先由 Flyway 建出表，否则预热读到的是空库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(10)
@ConditionalOnProperty(name = "lease.cache.warmup.enabled", havingValue = "true", matchIfMissing = true)
public class CacheWarmupRunner implements ApplicationRunner {

    private final PrdCategoryService categoryService;
    private final RoomService roomService;
    private final RechargeTierService rechargeTierService;
    private final MemberPurchaseService memberPurchaseService;
    private final MktCouponService couponService;

    @Override
    public void run(ApplicationArguments args) {
        int total = 5;
        int ok = 0;
        ok += warm("商品分类", categoryService::publicList);
        ok += warm("会议室", roomService::publicList);
        ok += warm("充值档位", rechargeTierService::publicList);
        ok += warm("会员等级", memberPurchaseService::publicLevels);
        ok += warm("优惠券模板", couponService::publicList);
        log.info("参照数据缓存预热完成：{}/{} 项（其余缓存按设计读时懒加载）", ok, total);
    }

    private int warm(String name, Supplier<? extends Collection<?>> loader) {
        try {
            int n = loader.get().size();
            log.info("  预热 {} 完成（{} 条）", name, n);
            return 1;
        } catch (Exception e) {
            log.warn("  预热 {} 失败（不影响启动，将回退为读时懒加载）", name, e);
            return 0;
        }
    }
}
