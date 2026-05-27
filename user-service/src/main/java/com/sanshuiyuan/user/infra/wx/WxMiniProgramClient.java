package com.sanshuiyuan.user.infra.wx;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Profile("!dev")
public class WxMiniProgramClient {

    // 微信 jscode2session 返回 JSON 但 Content-Type 为 text/plain，
    // 故以 String 读取再手动解析，避免依赖按 content-type 匹配的 JSON 转换器。
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RestTemplate restTemplate;
    private final String appId;
    private final String appSecret;

    public WxMiniProgramClient(
            RestTemplate restTemplate,
            @Value("${wx.miniprogram.app-id}") String appId,
            @Value("${wx.miniprogram.app-secret}") String appSecret) {
        this.restTemplate = restTemplate;
        this.appId = appId;
        this.appSecret = appSecret;
    }

    public WxSessionResponse code2session(String jsCode) {
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid={appId}&secret={appSecret}&js_code={jsCode}&grant_type=authorization_code";
        String body = restTemplate.getForObject(url, String.class,
                java.util.Map.of("appId", appId, "appSecret", appSecret, "jsCode", jsCode));
        if (body == null || body.isBlank()) {
            throw new RuntimeException("Empty response from WeChat API");
        }
        WxRawResponse response;
        try {
            response = MAPPER.readValue(body, WxRawResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse WeChat jscode2session response: " + body, e);
        }
        if (response.errcode() != null && response.errcode() != 0) {
            throw new RuntimeException("WeChat API error: " + response.errcode() + " - " + response.errmsg());
        }
        return new WxSessionResponse(response.openid(), response.unionid(), response.session_key());
    }

    public record WxSessionResponse(String openid, String unionid, String sessionKey) {}

    private record WxRawResponse(String openid, String session_key, String unionid, Integer errcode, String errmsg) {}
}
