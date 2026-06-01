package com.sanshuiyuan.h5.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * 调用微信 sns/jscode2session 用 jsCode 换 openid/unionid（小程序登录）。
 * appId/appSecret 仅后端持有；openid/unionid 属敏感信息，绝不入日志。
 */
public class HttpWxMiniAuthClient implements WxMiniAuthClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWxMiniAuthClient.class);
    private static final String SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final String appId;
    private final String appSecret;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpWxMiniAuthClient(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
    }

    @Override
    public MiniSession code2session(String jsCode) {
        // 微信 jscode2session 同样返回 Content-Type: text/plain，用 String 接收再手动解析。
        String raw;
        try {
            raw = restClient.get()
                    .uri(SESSION_URL + "?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code",
                            appId, appSecret, jsCode)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("微信小程序登录请求失败", e);
            throw new BizException(ErrorCode.WX_AUTH_FAILED, "微信登录服务暂不可用");
        }
        JsonNode body;
        try {
            body = objectMapper.readTree(raw);
        } catch (Exception e) {
            log.error("微信小程序登录响应解析失败 body={}", raw);
            throw new BizException(ErrorCode.WX_AUTH_FAILED, "微信登录服务响应异常");
        }
        if (body.hasNonNull("errcode") && body.get("errcode").asInt() != 0) {
            int errcode = body.path("errcode").asInt(-1);
            log.warn("微信小程序登录返回错误 errcode={} errmsg={}", errcode, body.path("errmsg").asText());
            throw new BizException(ErrorCode.WX_AUTH_FAILED, "微信登录失败，请重试");
        }
        String openid = body.path("openid").asText(null);
        if (openid == null || openid.isBlank()) {
            throw new BizException(ErrorCode.WX_AUTH_FAILED, "未获取到 openid");
        }
        String unionid = body.path("unionid").asText(null);
        String sessionKey = body.path("session_key").asText(null);
        return new MiniSession(openid, (unionid == null || unionid.isBlank()) ? null : unionid, sessionKey);
    }
}
