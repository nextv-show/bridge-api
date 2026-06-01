package com.sanshuiyuan.matching.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B.2.4 IP 兜底限频：POST /matching/requests 同 IP 60 次/分钟。
 * 手机号维度限频在 UseCase 内按 phone_hash 做（拦截器拿不到 body）。
 * 越限返回 429 + {"code":"RATE_LIMITED",...}。
 */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    @Value("${rate-limit.matching.ip-per-minute:60}")
    private int ipPerMinute;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IpRateLimitInterceptor())
                .addPathPatterns("/api/matching/requests");
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(ipPerMinute, Refill.intervally(ipPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private class IpRateLimitInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            // 仅限 POST（GET /mine、GET /{id} 走同前缀但不限 IP）。
            if (!HttpMethod.POST.matches(request.getMethod())) {
                return true;
            }
            String ip = clientIp(request);
            Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());
            if (bucket.tryConsume(1)) {
                return true;
            }
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
            return false;
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
