package com.sanshuiyuan.matching.request.application;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B.2.4 按手机号（phone_hash）发布限频：60s 1 次 + 24h 5 次（两个 Bandwidth 叠加）。
 * 在 UseCase 内消费（拦截器拿不到 body 手机号，按 hash 限频更可靠）。
 * ConcurrentHashMap<phoneHash, Bucket>，进程内（单实例足够；多实例可后续换 Redis）。
 */
@Component
public class PhoneRateLimiter {

    private final int perMinute;
    private final int perDay;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public PhoneRateLimiter(
            @Value("${rate-limit.matching.phone-per-minute:1}") int perMinute,
            @Value("${rate-limit.matching.phone-per-day:5}") int perDay) {
        this.perMinute = perMinute;
        this.perDay = perDay;
    }

    /** 尝试消费一次配额；超限返回 false。 */
    public boolean tryConsume(String phoneHash) {
        Bucket bucket = buckets.computeIfAbsent(phoneHash, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(perMinute, Refill.intervally(perMinute, Duration.ofMinutes(1))))
                .addLimit(Bandwidth.classic(perDay, Refill.intervally(perDay, Duration.ofDays(1))))
                .build());
        return bucket.tryConsume(1);
    }
}
