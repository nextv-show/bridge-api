package com.sanshuiyuan.cend.infra.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调用 user-service 的 /internal/users/sync-h5，将 H5 登录用户并入统一用户体系（spec 012）。
 * 使用 S2S header X-S2S-Token 鉴权（与 admin-service/UserDirectoryClient 一致）。
 */
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

    /** sync-h5 出参。 */
    public record SyncH5Result(Long userId, boolean isNew, boolean inviterBound) {
    }

    /**
     * 并号同步。
     *
     * @param openid    H5 公众号 openid（必填）。
     * @param unionid   微信 unionid（可空）。
     * @param inviterId 已由 RefIdCodec 解密的推广者 user_id（可空，仅首次创建写入关系链）。
     * @return 同步结果；user-service 不可达或返回错误时降级为 {@code null}，绝不抛出（不阻断 H5 登录）。
     */
    public SyncH5Result syncH5(String openid, String unionid, Long inviterId) {
        String url = baseUrl + "/internal/users/sync-h5";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-S2S-Token", s2sToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("openid", openid);
        body.put("unionid", unionid);
        body.put("inviterId", inviterId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    url, new HttpEntity<>(body, headers), Map.class);
            if (resp == null) {
                return null;
            }
            return new SyncH5Result(asLong(resp.get("userId")),
                    Boolean.TRUE.equals(resp.get("isNew")),
                    Boolean.TRUE.equals(resp.get("inviterBound")));
        } catch (RestClientException e) {
            // 降级：user-service 抖动/不可达不得阻断 H5 登录主流程。openid 属敏感信息，绝不入日志。
            log.warn("sync-h5 调用 user-service 失败，已降级（不影响 H5 登录）: {}", e.getMessage());
            return null;
        }
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        return Long.valueOf(o.toString());
    }
}
