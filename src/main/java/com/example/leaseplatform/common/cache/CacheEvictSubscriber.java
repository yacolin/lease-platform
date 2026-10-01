package com.example.leaseplatform.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * L1 失效广播订阅者：收到其它实例的失效通知后清掉本实例的 L1 条目。
 *
 * <p>必要性：L1 是<b>进程内</b>缓存，实例 A 更新数据后无法直接清掉实例 B 的 L1。
 * 没有这个广播，实例 B 的 L1 会一直脏到它的 L1 TTL 到期
 * （参考实现 products/cache.go 的单实例 L1 正是该问题，多副本部署时会读到旧值）。
 *
 * <p>注意：Redis Pub/Sub 会把消息也投递给发布者自身，因此本实例会收到自己刚清过的 key
 * ——重复失效无副作用（{@code invalidate} 幂等）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictSubscriber implements MessageListener {

    private final MultiLevelCache multiLevelCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String l2Key = new String(message.getBody(), StandardCharsets.UTF_8);
        multiLevelCache.evictLocal(l2Key);
        log.debug("收到 L1 失效广播，已清本地缓存: key={}", l2Key);
    }
}
