package com.sanshuiyuan.matching.request.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * P1-5 撮合反作弊/冲突埋点（design §7.1 §7.2）。Micrometer 计数器经 actuator/metrics 暴露，
 * 告警阈值在运维侧配置（如 quota_exceeded/rate_limited 突增 = 疑似扫单；conflict 突增 = 热门单争抢）。
 * 单 meter 名 + result 维度标签，便于聚合与比率告警。
 */
@Component
public class MatchingMetrics {

    private static final String CLAIM = "matching.claim";
    private static final String CONFIRM = "matching.confirm";

    private final MeterRegistry registry;

    public MatchingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void claimSuccess() {
        registry.counter(CLAIM, "result", "success").increment();
    }

    public void claimConflict() {
        registry.counter(CLAIM, "result", "conflict").increment();
    }

    public void claimRateLimited() {
        registry.counter(CLAIM, "result", "rate_limited").increment();
    }

    public void claimQuotaExceeded() {
        registry.counter(CLAIM, "result", "quota_exceeded").increment();
    }

    public void confirmConflict() {
        registry.counter(CONFIRM, "result", "conflict").increment();
    }
}
