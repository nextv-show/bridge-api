package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.request.domain.PriceTier;
import com.sanshuiyuan.matching.request.domain.SceneType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1-1 分桶评分纯逻辑单测（无 Spring）。 */
class RankScorerTest {

    private static final double[] REV = {3000, 1800, 1000, 500};
    private static final double[] DIST = {2, 5, 15, 50};
    private static final double[] FRESH = {24, 72, 168};

    @Test
    void monthlyGmv_grossCaliber() {
        // 1.2 元/升 × 90 升 × 30 = 3240
        assertEquals(3240L, RankScorer.monthlyGmv(PriceTier.T_120, 90));
        // 0.4 × 50 × 30 = 600
        assertEquals(600L, RankScorer.monthlyGmv(PriceTier.T_040, 50));
        // 1.5 × 100 × 30 = 4500
        assertEquals(4500L, RankScorer.monthlyGmv(PriceTier.T_150, 100));
    }

    @Test
    void distanceBucket_ascending() {
        assertEquals(100, RankScorer.bucketAscending(1.5, DIST, RankScorer.LADDER_5));
        assertEquals(100, RankScorer.bucketAscending(2.0, DIST, RankScorer.LADDER_5));   // 边界含
        assertEquals(80, RankScorer.bucketAscending(4.9, DIST, RankScorer.LADDER_5));
        assertEquals(60, RankScorer.bucketAscending(15, DIST, RankScorer.LADDER_5));
        assertEquals(40, RankScorer.bucketAscending(50, DIST, RankScorer.LADDER_5));
        assertEquals(20, RankScorer.bucketAscending(80, DIST, RankScorer.LADDER_5));     // 超末段
    }

    @Test
    void revenueBucket_descending() {
        assertEquals(100, RankScorer.bucketDescending(3240, REV, RankScorer.LADDER_5));
        assertEquals(100, RankScorer.bucketDescending(3000, REV, RankScorer.LADDER_5));  // 边界含
        assertEquals(80, RankScorer.bucketDescending(1800, REV, RankScorer.LADDER_5));
        assertEquals(60, RankScorer.bucketDescending(1000, REV, RankScorer.LADDER_5));
        assertEquals(40, RankScorer.bucketDescending(500, REV, RankScorer.LADDER_5));
        assertEquals(20, RankScorer.bucketDescending(499, REV, RankScorer.LADDER_5));    // 低于末段
    }

    @Test
    void freshBucket_fourLevels() {
        assertEquals(100, RankScorer.bucketAscending(10, FRESH, RankScorer.LADDER_FRESH));
        assertEquals(70, RankScorer.bucketAscending(72, FRESH, RankScorer.LADDER_FRESH));
        assertEquals(40, RankScorer.bucketAscending(168, FRESH, RankScorer.LADDER_FRESH));
        assertEquals(20, RankScorer.bucketAscending(200, FRESH, RankScorer.LADDER_FRESH));
    }

    @Test
    void sceneScore_neutralWhenNoPrefs() {
        assertEquals(RankScorer.SCENE_NEUTRAL,
                RankScorer.sceneScore(SceneType.HOME, EnumSet.noneOf(SceneType.class)));
        assertEquals(RankScorer.SCENE_HIT,
                RankScorer.sceneScore(SceneType.HOME, EnumSet.of(SceneType.HOME)));
        assertEquals(RankScorer.SCENE_MISS,
                RankScorer.sceneScore(SceneType.SHOP, EnumSet.of(SceneType.HOME)));
    }

    @Test
    void jitter_deterministicAndBounded() {
        double eps = 2.0;
        double a = RankScorer.jitter(12345L, eps);
        double b = RankScorer.jitter(12345L, eps);
        assertEquals(a, b, 0.0, "同 id 抖动必须稳定（翻页一致）");
        assertTrue(a >= 0 && a < eps, "抖动落 [0, epsilon)");
        // 不同 id 大概率不同（避免恒定秒杀同序）
        assertFalse(a == RankScorer.jitter(99999L, eps) && a == RankScorer.jitter(54321L, eps));
        assertEquals(0.0, RankScorer.jitter(12345L, 0), 0.0, "epsilon=0 关闭抖动");
    }

    @Test
    void ageHours_floorsAtZeroAndHandlesNull() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 12, 12, 0);
        assertEquals(5, RankScorer.ageHours(now.minusHours(5), now));
        assertEquals(0, RankScorer.ageHours(now.plusHours(3), now));   // 未来时间不为负
        assertEquals(Long.MAX_VALUE, RankScorer.ageHours(null, now));
    }

    @Test
    void reasons_priorityAndCap() {
        // 近(1km) + 高流水(3240≥1800) + 新单(2h) → 三标签
        List<String> r1 = RankScorer.reasons(1.0, 3240, 2, RankScorer.SCENE_NEUTRAL, DIST, REV, FRESH);
        assertEquals(List.of("近", "高流水", "新单"), r1);

        // 较近(4km) + 低流水 + 老单 → 仅「较近」
        List<String> r2 = RankScorer.reasons(4.0, 600, 200, RankScorer.SCENE_NEUTRAL, DIST, REV, FRESH);
        assertEquals(List.of("较近"), r2);

        // 命中场景但已满 3 个 → 不挤入「合口味」
        List<String> r3 = RankScorer.reasons(1.0, 3240, 2, RankScorer.SCENE_HIT, DIST, REV, FRESH);
        assertEquals(3, r3.size());
        assertFalse(r3.contains("合口味"));

        // 远 + 高流水 + 命中场景 → 「高流水」「合口味」
        List<String> r4 = RankScorer.reasons(40, 3240, 200, RankScorer.SCENE_HIT, DIST, REV, FRESH);
        assertEquals(List.of("高流水", "合口味"), r4);
    }
}
