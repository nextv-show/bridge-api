package com.sanshuiyuan.user.infra.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 出站调用 asset-service 内部接口（购机订单在 asset-service 独立库，user-service 查不到）。
 *
 * <p>带 {@code X-S2S-Token} 走 asset-service 的 {@code /internal/**}。容错风格参照 asset-service
 * {@code UserServiceClient.getOpenid}：任何 {@link RestClientException} / 空响应一律退化为空集，
 * <b>绝不向上抛</b>——「我的推荐」拿不到购买状态时仅按未购买展示，不阻塞主查询。
 */
@Component
public class AssetServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AssetServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String s2sToken;

    public AssetServiceClient(RestTemplate restTemplate,
                              @Value("${asset-service.base-url}") String baseUrl,
                              @Value("${asset-service.s2s-token}") String s2sToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.s2sToken = s2sToken;
    }

    /**
     * 给定 userId 集合，返回其中至少有一笔 PAID 订单（已购机）的 userId 子集。
     *
     * <p>空入参直接返回空集（不发起调用）；调用失败 / 空响应同样返回空集，永不抛异常。
     */
    public Set<Long> paidUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }
        String url = baseUrl + "/internal/orders/paid-user-ids";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-S2S-Token", s2sToken);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("userIds", userIds), headers);
        try {
            ResponseEntity<List> resp = restTemplate.postForEntity(url, request, List.class);
            List<?> body = resp.getBody();
            if (body == null) {
                return Collections.emptySet();
            }
            Set<Long> result = new HashSet<>();
            for (Object v : body) {
                Long id = toLong(v);
                if (id != null) {
                    result.add(id);
                }
            }
            return result;
        } catch (RestClientException e) {
            log.warn("paidUserIds failed for {} users: {}", userIds.size(), e.getMessage());
            return Collections.emptySet();
        }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
