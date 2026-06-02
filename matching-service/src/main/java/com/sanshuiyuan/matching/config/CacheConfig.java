package com.sanshuiyuan.matching.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/** Caffeine 缓存：matching_config 读取 60s 过期。 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String MATCHING_CONFIG_CACHE = "matchingConfig";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(MATCHING_CONFIG_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(64));
        return manager;
    }
}
