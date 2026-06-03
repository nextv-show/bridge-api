package com.sanshuiyuan.ess.infra.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 调用 user-service 内部接口，按 H5 会话 openid 解析 userId（合同 owner 校验用）。
 * S2S 鉴权头 {@code X-S2S-Token}，与 user-service {@code S2sTokenFilter} 期望一致。
 */
@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String s2sToken;

    public UserServiceClient(RestTemplate restTemplate,
                             @Value("${user-service.base-url:http://localhost:8081}") String baseUrl,
                             @Value("${user-service.s2s-token:local-dev-static-token}") String s2sToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.s2sToken = s2sToken;
    }

    /**
     * 按 openid 解析 userId。
     * <p>
     * 调用 {@code GET {base}/internal/users/by-openid?openid=}（带 X-S2S-Token）。
     * user-service 不可达 / 返回异常 / 查不到 → 返回 {@code null}（降级，由调用方据此拒绝）。
     */
    public Long resolveUserId(String openid) {
        if (openid == null || openid.isBlank()) {
            return null;
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/internal/users/by-openid")
                    .queryParam("openid", openid)
                    .toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-S2S-Token", s2sToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> body = response.getBody();
            if (body == null) {
                return null;
            }
            Object userId = body.get("userId");
            if (userId == null) {
                return null;
            }
            if (userId instanceof Number n) {
                return n.longValue();
            }
            return Long.valueOf(userId.toString());
        } catch (Exception e) {
            log.warn("解析 openid->userId 失败（降级返回 null）: {}", e.getMessage());
            return null;
        }
    }
}
