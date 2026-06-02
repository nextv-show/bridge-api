package com.sanshuiyuan.cend.wxmsg.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 微信公众号 access_token 管理。
 * 有效期 7200s，主动在 7000s 时刷新，防止临界过期。
 * 并发刷新用 Redis SET NX 锁保证单点执行。
 */
@Service
public class WxAccessTokenService {

    private static final Logger log = LoggerFactory.getLogger(WxAccessTokenService.class);

    private static final String TOKEN_KEY = "wx:access_token";
    private static final String LOCK_KEY  = "wx:access_token:refresh_lock";
    private static final long   TOKEN_TTL_SECONDS = 7000L;
    private static final long   LOCK_TTL_SECONDS  = 10L;
    private static final long   REFRESH_THRESHOLD  = 200L; // TTL 低于此值时触发刷新

    private final StringRedisTemplate redis;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wxpay.app-id:stub}")
    private String appId;

    @Value("${wxpay.app-secret:}")
    private String appSecret;

    public WxAccessTokenService(StringRedisTemplate redis) {
        this.redis = redis;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取有效的 access_token，缓存命中直接返回，临近过期则刷新。
     */
    public String getToken() {
        String token = redis.opsForValue().get(TOKEN_KEY);
        Long ttl = redis.getExpire(TOKEN_KEY, TimeUnit.SECONDS);

        if (token != null && ttl != null && ttl > REFRESH_THRESHOLD) {
            return token;
        }

        return refreshToken();
    }

    private String refreshToken() {
        Boolean locked = redis.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (Boolean.TRUE.equals(locked)) {
            try {
                String freshToken = fetchFromWechat();
                if (freshToken != null) {
                    redis.opsForValue().set(TOKEN_KEY, freshToken, Duration.ofSeconds(TOKEN_TTL_SECONDS));
                    return freshToken;
                }
            } finally {
                redis.delete(LOCK_KEY);
            }
        } else {
            // 另一线程正在刷新，稍等后读缓存
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String token = redis.opsForValue().get(TOKEN_KEY);
            if (token != null) {
                return token;
            }
        }

        // stub 模式或真实凭证未配置时返回空字符串，调用方统一处理降级
        return "";
    }

    private String fetchFromWechat() {
        if (isStub()) {
            log.debug("[wxmsg] app-secret 未配置，跳过 access_token 刷新（stub 模式）");
            return null;
        }
        try {
            String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                    + "&appid=" + appId + "&secret=" + appSecret;
            String body = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(body);
            if (node.has("access_token")) {
                return node.get("access_token").asText();
            }
            log.warn("[wxmsg] 获取 access_token 失败，响应: {}", body);
        } catch (Exception e) {
            log.error("[wxmsg] 获取 access_token 异常", e);
        }
        return null;
    }

    private boolean isStub() {
        return appSecret == null || appSecret.isBlank() || "stub".equals(appId);
    }
}
