package com.sanshuiyuan.evidence.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 上链失败的退避重试策略：1m、5m、30m、2h、8h，共 5 次。
 * 超过最大次数返回空，触发告警并置 FAILED。
 */
@Component
public class BackoffRetryPolicy {

    // 1m, 5m, 30m, 2h, 8h = 5 intervals
    private static final Duration[] DELAYS = {
        Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
        Duration.ofHours(2), Duration.ofHours(8)
    };
    private static final int MAX_RETRIES = 5;

    /** 返回第 retried 次失败后的下次延迟；达到最大次数返回空。 */
    public Optional<Duration> nextDelay(int retried) {
        if (retried >= MAX_RETRIES) return Optional.empty();
        return Optional.of(DELAYS[Math.min(retried, DELAYS.length - 1)]);
    }

    public int maxRetries() {
        return MAX_RETRIES;
    }
}
