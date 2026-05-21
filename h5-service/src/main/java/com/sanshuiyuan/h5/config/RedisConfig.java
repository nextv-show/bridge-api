package com.sanshuiyuan.h5.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * L2 分布式缓存（Redis）连接。plan §6：TTL 300s，多实例共享；
 * V1 失效靠 TTL，主动失效广播留到 105 写接口。
 * Redis 缺失时由 LandingConfigService 内的 try/catch 优雅降级到 DB（不阻断公开接口）。
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
