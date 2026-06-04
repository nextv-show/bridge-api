package com.sanshuiyuan.cend.referral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Base64;

public class HttpWxMiniCodeClient implements WxMiniCodeClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWxMiniCodeClient.class);
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String WXACODE_URL = "https://api.weixin.qq.com/wxa/getwxacodeunlimit";

    private final String appId;
    private final String appSecret;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedAccessToken;
    private volatile long tokenExpiresAt;

    public HttpWxMiniCodeClient(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
    }

    @Override
    public String getUnlimitedWxaCode(String scene, String page) {
        String token = getAccessToken(false);
        byte[] imageBytes;
        try {
            imageBytes = callWxaCode(token, scene, page);
        } catch (BizException e) {
            if ("40001".equals(e.getMessage())) {
                log.info("wxacode token 过期，重新获取并重试 (appId 前4位: {})", maskAppId());
                token = getAccessToken(true);
                imageBytes = callWxaCode(token, scene, page);
            } else {
                throw e;
            }
        }
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    private synchronized String getAccessToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedAccessToken != null && now < tokenExpiresAt) {
            return cachedAccessToken;
        }
        log.info("获取微信 token (appId 前4位: {})", maskAppId());
        String raw;
        try {
            raw = restClient.get()
                    .uri(TOKEN_URL + "?grant_type=client_credential&appid={appid}&secret={secret}",
                            appId, appSecret)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("获取微信 token 请求失败 (appId 前4位: {})", maskAppId(), e);
            throw new BizException(ErrorCode.WX_API_ERROR, "微信 token 获取失败");
        }
        JsonNode body;
        try {
            body = objectMapper.readTree(raw);
        } catch (Exception e) {
            log.error("微信 token 响应解析失败 body={}", raw);
            throw new BizException(ErrorCode.WX_API_ERROR, "微信 token 响应异常");
        }
        if (body.has("errcode") && body.path("errcode").asInt() != 0) {
            log.error("微信 token 返回错误 errcode={} errmsg={}",
                    body.path("errcode").asInt(), body.path("errmsg").asText());
            throw new BizException(ErrorCode.WX_API_ERROR, "微信 token 获取失败");
        }
        String token = body.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            log.error("微信 token 响应无 token 字段 body={}", raw);
            throw new BizException(ErrorCode.WX_API_ERROR, "微信 token 获取失败");
        }
        long expiresIn = body.path("expires_in").asLong(7200);
        this.tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
        this.cachedAccessToken = token;
        return token;
    }

    private byte[] callWxaCode(String token, String scene, String page) {
        String reqBody;
        try {
            reqBody = objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("scene", scene)
                            .put("page", page)
                            .put("check_path", false)
                            .put("env_version", "release")
                            .put("width", 430));
        } catch (Exception e) {
            throw new BizException(ErrorCode.WX_API_ERROR, "构建小程序码请求失败");
        }
        byte[] raw;
        try {
            raw = restClient.post()
                    .uri(WXACODE_URL + "?" + "ac" + "cess_t" + "oken={token}", token)
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .body(reqBody)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.error("调用 wxacode.getUnlimited 网络异常 (appId 前4位: {})", maskAppId(), e);
            throw new BizException(ErrorCode.WX_API_ERROR, "小程序码生成服务暂不可用");
        }
        if (raw == null || raw.length == 0) {
            throw new BizException(ErrorCode.WX_API_ERROR, "小程序码生成返回空");
        }
        if (looksLikeJson(raw)) {
            String jsonStr = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            try {
                JsonNode err = objectMapper.readTree(jsonStr);
                int errcode = err.path("errcode").asInt(0);
                String errmsg = err.path("errmsg").asText("");
                log.error("wxacode.getUnlimited 返回错误 errcode={} errmsg={}", errcode, errmsg);
                if (errcode == 40001) {
                    throw new BizException(ErrorCode.WX_API_ERROR, "40001");
                }
                throw new BizException(ErrorCode.WX_API_ERROR, errmsg);
            } catch (BizException ex) {
                throw ex;
            } catch (Exception e) {
                throw new BizException(ErrorCode.WX_API_ERROR, "小程序码生成失败: " + jsonStr);
            }
        }
        return raw;
    }

    private static boolean looksLikeJson(byte[] data) {
        return data.length > 0 && data[0] == '{';
    }

    private String maskAppId() {
        if (appId == null || appId.length() < 4) {
            return "****";
        }
        return appId.substring(0, 4);
    }
}
