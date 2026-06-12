package com.sanshuiyuan.matching.request.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** P1-5 撮合埋点计数器单测（SimpleMeterRegistry，无 Spring）。 */
class MatchingMetricsTest {

    private final SimpleMeterRegistry reg = new SimpleMeterRegistry();
    private final MatchingMetrics metrics = new MatchingMetrics(reg);

    private double count(String name, String result) {
        return reg.get(name).tag("result", result).counter().count();
    }

    @Test
    void claimCountersByResultTag() {
        metrics.claimSuccess();
        metrics.claimSuccess();
        metrics.claimConflict();
        metrics.claimRateLimited();
        metrics.claimQuotaExceeded();

        assertEquals(2.0, count("matching.claim", "success"));
        assertEquals(1.0, count("matching.claim", "conflict"));
        assertEquals(1.0, count("matching.claim", "rate_limited"));
        assertEquals(1.0, count("matching.claim", "quota_exceeded"));
    }

    @Test
    void confirmConflictCounter() {
        metrics.confirmConflict();
        metrics.confirmConflict();
        assertEquals(2.0, count("matching.confirm", "conflict"));
    }
}
