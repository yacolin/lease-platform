package com.example.leaseplatform.common.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 缓存失效广播的 Redis 订阅容器。
 *
 * <p>只订阅 {@link MultiLevelCache#EVICT_CHANNEL} 一个频道，用于多实例下的 L1 失效。
 * 容器在 Redis 短暂不可用时不会阻塞应用启动（内部按 recoveryInterval 重连）。
 */
@Configuration
public class CacheEvictConfig {

    @Bean
    public RedisMessageListenerContainer cacheEvictListenerContainer(
            RedisConnectionFactory connectionFactory, CacheEvictSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(MultiLevelCache.EVICT_CHANNEL));
        return container;
    }
}
