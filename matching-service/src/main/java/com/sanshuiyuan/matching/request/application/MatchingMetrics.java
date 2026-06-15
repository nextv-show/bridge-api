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
    private static final String FULFILL = "matching.fulfill";
    private static final String ACTIVATE = "matching.activate";

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

    /** 撮合履约：需求 → FULFILLED、设备 → PENDING_ACTIVATE（最终激活前置）。 */
    public void fulfilled() {
        registry.counter(FULFILL, "result", "success").increment();
    }

    /** 029 设备激活：首个心跳推进 PENDING_ACTIVATE → STAGE_1 成功。 */
    public void activated() {
        registry.counter(ACTIVATE, "result", "success").increment();
    }

    /** 029 激活幂等 no-op：无此 SN / 非 PENDING_ACTIVATE（重复心跳、未履约设备上线）。 */
    public void activateNoop() {
        registry.counter(ACTIVATE, "result", "noop").increment();
    }
}
