package com.sanshuiyuan.h5.wx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.h5.wxmsg.infra.WxAccessTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 微信公众号 jsapi_ticket 管理（009 T9.3）。
 *
 * <p>jsapi_ticket 用于生成 JS-SDK 权限验证签名，有效期 7200s（与 access_token 同级）。
 * 与 {@link WxAccessTokenService} 同构：Redis 缓存 + 临近过期主动刷新 + SET NX 单点刷新锁，
 * 避免每次请求都打微信接口（微信对 ticket 调用有日配额）。
 *
 * <p>stub / 未配置 appSecret 时返回空串，调用方据此降级（不下发签名，前端跳过 wx.config）。
 */
@Service
public class WxJsapiTicketService {

    private static final Logger log = LoggerFactory.getLogger(WxJsapiTicketService.class);

    private static final String TICKET_KEY = "wx:jsapi_ticket";
    private static final String LOCK_KEY = "wx:jsapi_ticket:refresh_lock";
    private static final long TICKET_TTL_SECONDS = 7000L;   // 微信有效期 7200s，提前 200s 失效防临界
    private static final long LOCK_TTL_SECONDS = 10L;
    private static final long REFRESH_THRESHOLD = 200L;      // 剩余 TTL 低于此值即刷新

    private final StringRedisTemplate redis;
    private final WxAccessTokenService accessTokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WxJsapiTicketService(StringRedisTemplate redis, WxAccessTokenService accessTokenService) {
        this.redis = redis;
        this.accessTokenService = accessTokenService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 取有效 jsapi_ticket；缓存命中直接返回，临近过期则刷新。
     * 未配置真实凭证或刷新失败时返回空串，由调用方降级。
     */
    public String getTicket() {
        String ticket = redis.opsForValue().get(TICKET_KEY);
        Long ttl = redis.getExpire(TICKET_KEY, TimeUnit.SECONDS);
        if (ticket != null && !ticket.isBlank() && ttl != null && ttl > REFRESH_THRESHOLD) {
            return ticket;
        }
        return refreshTicket();
    }

    private String refreshTicket() {
        Boolean locked = redis.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
        if (Boolean.TRUE.equals(locked)) {
            try {
                String fresh = fetchFromWechat();
                if (fresh != null && !fresh.isBlank()) {
                    redis.opsForValue().set(TICKET_KEY, fresh, Duration.ofSeconds(TICKET_TTL_SECONDS));
                    return fresh;
                }
            } finally {
                redis.delete(LOCK_KEY);
            }
        } else {
            // 另一线程正在刷新，稍等后读缓存。
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String ticket = redis.opsForValue().get(TICKET_KEY);
            if (ticket != null && !ticket.isBlank()) {
                return ticket;
            }
        }
        return "";
    }

    private String fetchFromWechat() {
        String accessToken = accessTokenService.getToken();
        if (accessToken == null || accessToken.isBlank()) {
            log.debug("[wx-jssdk] access_token 不可用（stub / 未配置），跳过 jsapi_ticket 刷新");
            return null;
        }
        try {
            String url = "https://api.weixin.qq.com/cgi-bin/ticket/getticket"
                    + "?access_token=" + accessToken + "&type=jsapi";
            String body = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(body);
            if (node.path("errcode").asInt(0) == 0 && node.hasNonNull("ticket")) {
                return node.get("ticket").asText();
            }
            log.warn("[wx-jssdk] 获取 jsapi_ticket 失败，响应: {}", body);
        } catch (Exception e) {
            log.error("[wx-jssdk] 获取 jsapi_ticket 异常", e);
        }
        return null;
    }
}
