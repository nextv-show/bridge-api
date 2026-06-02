package com.sanshuiyuan.matching.request.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 同 request_id 的软限流：200 QPS 令牌桶。 */
@Component
public class ClaimRateLimiter {

    private final Cache<Long, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(2))
            .build();

    public ClaimRateLimiter(@Value("${rate-limit.matching.claim-qps:200}") int qps) {
        this.qps = qps;
    }

    private final int qps;

    public boolean tryConsume(long requestId) {
        Bucket bucket = buckets.get(requestId, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(qps, Refill.greedy(qps, Duration.ofSeconds(1))))
                .build());
        return bucket.tryConsume(1);
    }
}
