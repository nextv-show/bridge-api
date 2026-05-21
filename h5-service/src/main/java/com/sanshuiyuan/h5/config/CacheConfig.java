package com.sanshuiyuan.h5.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * L1 本地缓存（Caffeine）。plan §6：TTL 60s / maxSize 4，单键 landing:config:published。
 * L2（Redis）与「上一份合规快照」兜底在 LandingConfigService 内手动编排（需在回填前先做合规校验）。
 */
@Configuration
public class CacheConfig {

    public static final String LANDING_CONFIG_CACHE = "landingConfig";

    @Value("${landing.cache.caffeine-ttl-seconds:60}")
    private long ttlSeconds;

    @Value("${landing.cache.caffeine-max-size:4}")
    private long maxSize;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(LANDING_CONFIG_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize));
        return manager;
    }
}
