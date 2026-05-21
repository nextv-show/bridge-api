package com.sanshuiyuan.h5.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公开接口限频（bucket4j，按 IP 令牌桶）。plan §5.1：同 IP 120 次/分钟。
 * 越限返回 429 + {@link com.sanshuiyuan.h5.common.ApiResponse} 形态的 JSON。
 * 沿用 user-service 既有模式，作用于 /api/h5/**。
 */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    @Value("${rate-limit.landing.max-requests:120}")
    private int maxRequests;

    @Value("${rate-limit.landing.per-minutes:1}")
    private int perMinutes;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor())
                .addPathPatterns("/api/h5/**");
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(maxRequests, Refill.intervally(maxRequests, Duration.ofMinutes(perMinutes)));
        return Bucket.builder().addLimit(limit).build();
    }

    private class RateLimitInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            String ip = request.getRemoteAddr();
            Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());
            if (bucket.tryConsume(1)) {
                return true;
            }
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后再试\",\"data\":null,\"traceId\":null}");
            return false;
        }
    }
}
