package com.sanshuiyuan.water.session.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanshuiyuan.water.common.BizException;
import com.sanshuiyuan.water.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简易固定窗口限频（单实例内存版，Caffeine 1 分钟窗口）。
 * 用于 {@code POST /api/w/water/sessions} 等敏感写操作的 30/min/user 限制。
 */
@Component
public class WaterRateLimiter {

    private static final int START_LIMIT_PER_MIN = 30;

    private final Cache<String, AtomicInteger> windows = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(100_000)
            .build();

    /** 校验开启取水频率：超过 30/min 抛 RATE_LIMITED。 */
    public void checkStart(Long userId) {
        AtomicInteger counter = windows.get("start:" + userId, k -> new AtomicInteger(0));
        if (counter.incrementAndGet() > START_LIMIT_PER_MIN) {
            throw new BizException(ErrorCode.RATE_LIMITED);
        }
    }
}
