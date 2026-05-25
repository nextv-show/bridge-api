package com.sanshuiyuan.admin.infra.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 调用 user-service 的 /internal/users 分页导出接口，拉取真实用户目录。
 * 使用 S2S header X-S2S-Token 鉴权（与 asset-service 一致）。
 */
@Component
public class UserDirectoryClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String s2sToken;

    public UserDirectoryClient(RestTemplate restTemplate,
                               @Value("${user-service.base-url}") String baseUrl,
                               @Value("${user-service.s2s-token}") String s2sToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.s2sToken = s2sToken;
    }

    /** user-service 用户目录条目（createdAt 为 ISO-8601 字符串）。 */
    public record SourceUser(
            Long id,
            String unionid,
            String openidWx,
            String openidApp,
            String nickname,
            String avatarUrl,
            String activeRole,
            String createdAt) {
    }

    /** 单页结果。 */
    public record Page(List<SourceUser> items, long total, int page, int size) {
    }

    /**
     * 拉取一页用户目录。
     * @throws RestClientException 当 user-service 不可达或返回错误时
     */
    public Page fetchPage(int page, int size) {
        String url = baseUrl + "/internal/users?page=" + page + "&size=" + size;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-S2S-Token", s2sToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, request,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, Object> body = response.getBody();
        if (body == null) {
            return new Page(List.of(), 0L, page, size);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems =
                (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        List<SourceUser> items = rawItems.stream().map(m -> new SourceUser(
                asLong(m.get("id")),
                asString(m.get("unionid")),
                asString(m.get("openidWx")),
                asString(m.get("openidApp")),
                asString(m.get("nickname")),
                asString(m.get("avatarUrl")),
                asString(m.get("activeRole")),
                asString(m.get("createdAt")))).toList();

        long total = asLong(body.getOrDefault("total", (long) items.size()));
        return new Page(items, total, page, size);
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        return Long.valueOf(o.toString());
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }
}
