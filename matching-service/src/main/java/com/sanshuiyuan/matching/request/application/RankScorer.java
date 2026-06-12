package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.PriceTier;
import com.sanshuiyuan.matching.request.domain.SceneType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * P1-1 撮合「偏好排序」分桶评分（design-preference-matching.md §2 §3）。
 *
 * <p>四分量分桶（收益/距离/场景/新鲜）加权得 0–100 的 rankScore，仅用于后端排序，
 * <b>不向前台暴露数值分</b>。每条另反推 ≤3 个「推荐原因」标签（§2.4）。
 *
 * <p>分段评分阶梯（100/80/60/40/20、新鲜 100/70/40/20）为常量；桶边界与权重读
 * {@link MatchingConfigService}（运营可调）。纯计算逻辑做成 static，便于无 Spring 单测。
 */
@Component
public class RankScorer {

    /** 收益/距离 5 段阶梯。 */
    static final double[] LADDER_5 = {100, 80, 60, 40, 20};
    /** 新鲜度 4 段阶梯。 */
    static final double[] LADDER_FRESH = {100, 70, 40, 20};

    static final double SCENE_HIT = 100;
    static final double SCENE_NEUTRAL = 60;   // 购机者无偏好（P1 无偏好沉淀，恒中性）
    static final double SCENE_MISS = 30;

    private final MatchingConfigService config;

    public RankScorer(MatchingConfigService config) {
        this.config = config;
    }

    /** 单条评分结果。 */
    public record Ranked(double score, BigDecimal monthlyGmv, List<String> reasons) {}

    /**
     * @param scenePrefs 购机者偏好场景集合（P1 传空集 → 场景分恒中性 60；P3 偏好沉淀后填充）
     */
    public Ranked rank(MatchingRequest r, double distanceKm, Set<SceneType> scenePrefs, LocalDateTime now) {
        long gmv = monthlyGmv(r.getExpectedPriceTier(), r.getEstDailyLiters());
        long ageHours = ageHours(r.getCreatedAt(), now);

        double sRev = bucketDescending(gmv, config.revenueBuckets(), LADDER_5);
        double sDist = bucketAscending(distanceKm, config.distanceBucketsKm(), LADDER_5);
        double sScene = sceneScore(r.getSceneType(), scenePrefs);
        double sFresh = bucketAscending(ageHours, config.freshBucketsHours(), LADDER_FRESH);

        double score = config.matchWeightRevenue() * sRev
                + config.matchWeightDistance() * sDist
                + config.matchWeightScene() * sScene
                + config.matchWeightFresh() * sFresh;

        List<String> reasons = reasons(distanceKm, gmv, ageHours, sScene,
                config.distanceBucketsKm(), config.revenueBuckets(), config.freshBucketsHours());
        return new Ranked(score, BigDecimal.valueOf(gmv), reasons);
    }

    /** 预估月流水（元，毛口径）：tier元/升 × 日用水量 × 30。 */
    public static long monthlyGmv(PriceTier tier, int estDailyLiters) {
        return tier.yuanPerLiter()
                .multiply(BigDecimal.valueOf(estDailyLiters))
                .multiply(BigDecimal.valueOf(30))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }

    static long ageHours(LocalDateTime createdAt, LocalDateTime now) {
        if (createdAt == null) return Long.MAX_VALUE;
        long h = Duration.between(createdAt, now).toHours();
        return Math.max(0, h);
    }

    /** 升序桶：value ≤ bounds[i] → ladder[i]；都不满足 → ladder[last]。ladder.length == bounds.length+1。 */
    static double bucketAscending(double value, double[] bounds, double[] ladder) {
        int n = Math.min(bounds.length, ladder.length - 1);
        for (int i = 0; i < n; i++) {
            if (value <= bounds[i]) return ladder[i];
        }
        return ladder[ladder.length - 1];
    }

    /** 降序桶：value ≥ bounds[i] → ladder[i]；都不满足 → ladder[last]。 */
    static double bucketDescending(double value, double[] bounds, double[] ladder) {
        int n = Math.min(bounds.length, ladder.length - 1);
        for (int i = 0; i < n; i++) {
            if (value >= bounds[i]) return ladder[i];
        }
        return ladder[ladder.length - 1];
    }

    static double sceneScore(SceneType scene, Set<SceneType> prefs) {
        if (prefs == null || prefs.isEmpty()) return SCENE_NEUTRAL;
        return prefs.contains(scene) ? SCENE_HIT : SCENE_MISS;
    }

    /**
     * 同桶稳定抖动（design §2.5）：基于 id 的确定性扰动 [0, epsilon)，
     * 保证翻页/重复请求顺序一致（不可用 Math.random），又让同分桶单不被永远秒走。
     */
    public static double jitter(long id, double epsilon) {
        if (epsilon <= 0) return 0;
        long h = Long.hashCode(id * 2654435761L) & 0xffff;   // 16-bit 稳定散列
        return (h / 65535.0) * epsilon;
    }

    /** 反推推荐原因（≤3，优先级：近 > 高流水 > 新单 > 合口味）。 */
    static List<String> reasons(double distanceKm, long gmv, long ageHours, double sceneScore,
                                double[] distBounds, double[] revBounds, double[] freshBounds) {
        List<String> out = new ArrayList<>(3);
        if (distBounds.length >= 1 && distanceKm <= distBounds[0]) {
            out.add("近");
        } else if (distBounds.length >= 2 && distanceKm <= distBounds[1]) {
            out.add("较近");
        }
        if (revBounds.length >= 2 && gmv >= revBounds[1]) {
            out.add("高流水");
        }
        if (freshBounds.length >= 1 && ageHours <= freshBounds[0]) {
            out.add("新单");
        }
        if (sceneScore >= SCENE_HIT && out.size() < 3) {
            out.add("合口味");
        }
        return out.size() > 3 ? out.subList(0, 3) : out;
    }
}
