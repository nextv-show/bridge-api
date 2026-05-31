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

    /**
     * 取某用户的两级邀请关系链快照 {inviterId(L1), grandInviterId(L2)}。
     *
     * <p>GET {@code ${user-service.base-url}/internal/users/{id}/referral-chain}，带 X-S2S-Token。
     * 契约由 user-service 同期实现，期望响应体形如：
     * <pre>{ "inviterId": 123, "grandInviterId": 45 }</pre>
     * 二者均可为 null（自然流量 / 无上级 / 仅一级）。
     *
     * <p><b>容错（参照 {@link #getOpenid}）：</b>任何 RestClientException 或空响应一律返回
     * {@link ReferralChain#EMPTY}（两个 id 均 null）——绝不向上抛，以免阻塞/回滚支付主流程；
     * 关系链取不到则该单不产生返利（宁可不返也不错返）。
     *
     * <p><b>合规：</b>仅取这两级，asset-service 不在本地做任何向上递归追溯（L3+ 物理隔离）。
     */
    public ReferralChain getReferralChain(Long userId) {
        String url = baseUrl + "/internal/users/" + userId + "/referral-chain";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-S2S-Token", s2sToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            Map<?, ?> body = resp.getBody();
            if (body == null) {
                return ReferralChain.EMPTY;
            }
            return new ReferralChain(toLong(body.get("inviterId")), toLong(body.get("grandInviterId")));
        } catch (RestClientException e) {
            log.warn("getReferralChain failed for user {}: {}", userId, e.getMessage());
            return ReferralChain.EMPTY;
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

    /**
     * 两级邀请关系链快照。{@code inviterId}=L1 直接邀请人，{@code grandInviterId}=L2 间接邀请人；
     * 任一为 null 表示该级不存在。<b>仅两级，严禁扩展为更深层次。</b>
     */
    public record ReferralChain(Long inviterId, Long grandInviterId) {
        public static final ReferralChain EMPTY = new ReferralChain(null, null);
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
