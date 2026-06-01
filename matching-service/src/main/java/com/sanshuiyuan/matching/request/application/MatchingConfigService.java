package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.config.CacheConfig;
import com.sanshuiyuan.matching.request.domain.MatchingConfig;
import com.sanshuiyuan.matching.request.infra.MatchingConfigRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** matching_config 读取（Caffeine 60s 缓存）。缺键回退默认值。 */
@Service
public class MatchingConfigService {

    static final String K_LOCK_TTL_DAYS = "lock.ttl.days";
    static final String K_LOCK_MAX_PER_OWNER = "lock.max.per.owner";
    static final String K_NEARBY_DEFAULT_RADIUS = "nearby.default.radius.km";
    static final String K_NEARBY_MAX_RADIUS = "nearby.max.radius.km";

    private final MatchingConfigRepository repo;

    public MatchingConfigService(MatchingConfigRepository repo) {
        this.repo = repo;
    }

    @Cacheable(cacheNames = CacheConfig.MATCHING_CONFIG_CACHE, key = "#key")
    public String getRaw(String key, String defaultValue) {
        return repo.findById(key).map(MatchingConfig::getConfigValue).orElse(defaultValue);
    }

    private int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getRaw(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int lockTtlDays() {
        return getInt(K_LOCK_TTL_DAYS, 7);
    }

    public int lockMaxPerOwner() {
        return getInt(K_LOCK_MAX_PER_OWNER, 5);
    }

    public int nearbyDefaultRadiusKm() {
        return getInt(K_NEARBY_DEFAULT_RADIUS, 50);
    }

    public int nearbyMaxRadiusKm() {
        return getInt(K_NEARBY_MAX_RADIUS, 200);
    }
}
