package com.sanshuiyuan.asset.infra.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String s2sToken;

    public UserServiceClient(RestTemplate restTemplate,
                             @Value("${user-service.base-url}") String baseUrl,
                             @Value("${user-service.s2s-token}") String s2sToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.s2sToken = s2sToken;
    }

    // D.2.4: 5 次指数退避重试；耗尽后进入 @Recover 告警，不向上抛以免影响支付主流程
    @Retryable(retryFor = RestClientException.class, maxAttempts = 5,
            backoff = @Backoff(delay = 500, multiplier = 2.0))
    public void addOwnerRole(Long userId) {
        String url = baseUrl + "/internal/users/" + userId + "/roles";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-S2S-Token", s2sToken);

        Map<String, String> body = Map.of("role", "OWNER");
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(url, request, Void.class);
    }

    @Recover
    public void addOwnerRoleRecover(RestClientException ex, Long userId) {
        // TODO: 接入告警渠道（飞书/钉钉）；当前落 ERROR 日志，后续可由对账补偿
        log.error("ALERT: addOwnerRole exhausted retries for user {}: {}", userId, ex.getMessage());
    }

    /** 按 userId 取微信 openid（小程序 JSAPI 支付 payer）。失败返回 null，由上层兜底。 */
    public String getOpenid(Long userId) {
        String url = baseUrl + "/internal/users/" + userId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-S2S-Token", s2sToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            Object openid = resp.getBody() != null ? resp.getBody().get("openidWx") : null;
            return openid != null ? openid.toString() : null;
        } catch (RestClientException e) {
            log.warn("getOpenid failed for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
}
