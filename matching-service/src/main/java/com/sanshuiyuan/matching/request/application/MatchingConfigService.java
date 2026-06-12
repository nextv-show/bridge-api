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

    // P1-1 偏好排序（V016）
    static final String K_WEIGHT_REVENUE = "match.weight.revenue";
    static final String K_WEIGHT_DISTANCE = "match.weight.distance";
    static final String K_WEIGHT_SCENE = "match.weight.scene";
    static final String K_WEIGHT_FRESH = "match.weight.fresh";
    static final String K_REVENUE_BUCKETS = "match.revenue.buckets";
    static final String K_DISTANCE_BUCKETS = "match.distance.buckets.km";
    static final String K_FRESH_BUCKETS = "match.fresh.buckets.hours";
    static final String K_JITTER_EPSILON = "match.jitter.epsilon";
    static final String K_NEARBY_CANDIDATE_LIMIT = "nearby.candidate.limit";

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

    private double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(getRaw(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** CSV → double[]，任一段非法整体回退默认。 */
    private double[] getDoubleArray(String key, double[] defaultValue) {
        String raw = getRaw(key, null);
        if (raw == null || raw.isBlank()) return defaultValue;
        String[] parts = raw.trim().split(",");
        double[] out = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Double.parseDouble(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return defaultValue;
        }
        return out;
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

    // ─── P1-1 偏好排序（design §2 §5.3） ───────────────────────────

    public double matchWeightRevenue() {
        return getDouble(K_WEIGHT_REVENUE, 0.45);
    }

    public double matchWeightDistance() {
        return getDouble(K_WEIGHT_DISTANCE, 0.30);
    }

    public double matchWeightScene() {
        return getDouble(K_WEIGHT_SCENE, 0.15);
    }

    public double matchWeightFresh() {
        return getDouble(K_WEIGHT_FRESH, 0.10);
    }

    /** 收益分桶边界（月流水，元；降序）。≥buckets[0]→100…，最后一段→20。 */
    public double[] revenueBuckets() {
        return getDoubleArray(K_REVENUE_BUCKETS, new double[]{3000, 1800, 1000, 500});
    }

    /** 距离分桶边界（km；升序）。≤buckets[0]→100…，最后一段→20。 */
    public double[] distanceBucketsKm() {
        return getDoubleArray(K_DISTANCE_BUCKETS, new double[]{2, 5, 15, 50});
    }

    /** 新鲜度分桶边界（小时；升序）。≤buckets[0]→100…，最后一段→20。 */
    public double[] freshBucketsHours() {
        return getDoubleArray(K_FRESH_BUCKETS, new double[]{24, 72, 168});
    }

    /** recommended 同桶随机抖动幅度（分）。 */
    public double jitterEpsilon() {
        return getDouble(K_JITTER_EPSILON, 2);
    }

    /** nearby 第一层候选上限（DB LIMIT）。 */
    public int nearbyCandidateLimit() {
        return getInt(K_NEARBY_CANDIDATE_LIMIT, 2000);
    }
}
