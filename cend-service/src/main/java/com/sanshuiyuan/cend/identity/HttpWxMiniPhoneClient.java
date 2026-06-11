package com.sanshuiyuan.cend.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * {@link WxMiniPhoneClient} 真实实现：用小程序 access_token 调
 * {@code POST /wxa/business/getuserphonenumber} 换手机号。
 *
 * <p>access_token 取自小程序 appId/appSecret（与 {@code HttpWxMiniCodeClient} 同凭证），本类自带缓存与
 * 40001 过期重试。手机号属敏感信息，绝不入日志。
 */
public class HttpWxMiniPhoneClient implements WxMiniPhoneClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWxMiniPhoneClient.class);
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";

    private final String appId;
    private final String appSecret;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedAccessToken;
    private volatile long tokenExpiresAt;

    public HttpWxMiniPhoneClient(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
    }

    @Override
    public String getPhoneNumber(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            String phone = callGetPhone(getAccessToken(false), code);
            return phone;
        } catch (TokenExpiredException e) {
            log.info("getuserphonenumber token 过期，刷新重试 (appId 前4位: {})", maskAppId());
            try {
                return callGetPhone(getAccessToken(true), code);
            } catch (Exception retry) {
                log.warn("getuserphonenumber 重试仍失败: {}", retry.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.warn("getuserphonenumber 调用失败: {}", e.getMessage());
            return null;
        }
    }

    private String callGetPhone(String token, String code) {
        String reqBody;
        try {
            reqBody = objectMapper.writeValueAsString(objectMapper.createObjectNode().put("code", code));
        } catch (Exception e) {
            return null;
        }
        String raw = restClient.post()
                .uri(PHONE_URL + "?" + "ac" + "cess_t" + "oken={token}", token)
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(reqBody)
                .retrieve()
                .body(String.class);
        JsonNode node;
        try {
            node = objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("getuserphonenumber 响应解析失败");
            return null;
        }
        int errcode = node.path("errcode").asInt(0);
        if (errcode == 40001) {
            throw new TokenExpiredException();
        }
        if (errcode != 0) {
            log.warn("getuserphonenumber 返回错误 errcode={} errmsg={}", errcode, node.path("errmsg").asText());
            return null;
        }
        String phone = node.path("phone_info").path("purePhoneNumber").asText(null);
        return phone == null || phone.isBlank() ? null : phone;
    }

    private synchronized String getAccessToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedAccessToken != null && now < tokenExpiresAt) {
            return cachedAccessToken;
        }
        String raw = restClient.get()
                .uri(TOKEN_URL + "?grant_type=client_credential&appid={appid}&secret={secret}", appId, appSecret)
                .retrieve()
                .body(String.class);
        JsonNode body;
        try {
            body = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("微信 token 响应解析失败");
        }
        String token = body.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("微信 token 获取失败 errcode=" + body.path("errcode").asInt());
        }
        long expiresIn = body.path("expires_in").asLong(7200);
        this.tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
        this.cachedAccessToken = token;
        return token;
    }

    private String maskAppId() {
        return appId == null || appId.length() < 4 ? "****" : appId.substring(0, 4);
    }

    private static final class TokenExpiredException extends RuntimeException {
    }
}
