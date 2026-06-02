package com.sanshuiyuan.matching.request.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * B.2.4 按手机号（phone_hash）发布限频：60s 1 次 + 24h 5 次（两个 Bandwidth 叠加）。
 * 在 UseCase 内消费（拦截器拿不到 body 手机号，按 hash 限频更可靠）。
 * 用有界 Caffeine（maximumSize + expireAfterAccess）替代裸 Map，避免按手机号无限增长的内存泄漏/DoS。
 * 闲置 >25h 的 bucket 被淘汰即可：24h 窗口届时已满额刷新，重建等价。进程内（多实例可后续换 Redis）。
 */
@Component
public class PhoneRateLimiter {

    private final int perMinute;
    private final int perDay;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(200_000)
            .expireAfterAccess(Duration.ofHours(25))
            .build();

    public PhoneRateLimiter(
            @Value("${rate-limit.matching.phone-per-minute:1}") int perMinute,
            @Value("${rate-limit.matching.phone-per-day:5}") int perDay) {
        this.perMinute = perMinute;
        this.perDay = perDay;
    }

    /** 尝试消费一次配额；超限返回 false。 */
    public boolean tryConsume(String phoneHash) {
        Bucket bucket = buckets.get(phoneHash, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(perMinute, Refill.intervally(perMinute, Duration.ofMinutes(1))))
                .addLimit(Bandwidth.classic(perDay, Refill.intervally(perDay, Duration.ofDays(1))))
                .build());
        return bucket.tryConsume(1);
    }
}
