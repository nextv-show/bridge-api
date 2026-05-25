package com.sanshuiyuan.h5.wxmsg.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 检查用户是否关注微信服务号。
 * 结果 Redis 缓存 1h（wx:subscribe:{openid}），降低重复调用微信 API 的频率。
 * stub 模式（app-secret 未配置）时默认返回 true，使推送正常进入发送流程（由 access_token 决定最终行为）。
 */
@Component
public class WxSubscribeChecker {

    private static final Logger log = LoggerFactory.getLogger(WxSubscribeChecker.class);
    private static final String CACHE_KEY_PREFIX = "wx:subscribe:";

    private final StringRedisTemplate redis;
    private final WxAccessTokenService tokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wx.subscribe.cache-ttl-seconds:3600}")
    private long cacheTtlSeconds;

    @Value("${wxpay.app-secret:}")
    private String appSecret;

    public WxSubscribeChecker(StringRedisTemplate redis, WxAccessTokenService tokenService) {
        this.redis = redis;
        this.tokenService = tokenService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isSubscribed(String openid) {
        String cacheKey = CACHE_KEY_PREFIX + openid;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return "1".equals(cached);
        }

        boolean subscribed = fetchSubscribeStatus(openid);
        try {
            redis.opsForValue().set(cacheKey, subscribed ? "1" : "0",
                    Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception e) {
            log.warn("[wxmsg] 缓存关注状态失败，忽略", e);
        }
        return subscribed;
    }

    private boolean fetchSubscribeStatus(String openid) {
        if (isStub()) {
            // stub 模式：视为已关注，推送流程继续（access_token 为空时自然失败并被记录）
            return true;
        }
        try {
            String token = tokenService.getToken();
            if (token.isBlank()) return false;

            String url = "https://api.weixin.qq.com/cgi-bin/user/info?access_token="
                    + token + "&openid=" + openid + "&lang=zh_CN";
            String body = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(body);
            return node.has("subscribe") && node.get("subscribe").asInt() == 1;
        } catch (Exception e) {
            log.warn("[wxmsg] 查询关注状态失败，openid={}", openid, e);
            return false;
        }
    }

    private boolean isStub() {
        return appSecret == null || appSecret.isBlank();
    }
}
