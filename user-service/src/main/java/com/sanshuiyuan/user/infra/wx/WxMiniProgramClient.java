package com.sanshuiyuan.user.infra.wx;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WxMiniProgramClient {

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
        var response = restTemplate.getForObject(url, WxRawResponse.class,
                java.util.Map.of("appId", appId, "appSecret", appSecret, "jsCode", jsCode));
        if (response == null) {
            throw new RuntimeException("Empty response from WeChat API");
        }
        if (response.errcode() != null && response.errcode() != 0) {
            throw new RuntimeException("WeChat API error: " + response.errcode() + " - " + response.errmsg());
        }
        return new WxSessionResponse(response.openid(), response.unionid(), response.session_key());
    }

    public record WxSessionResponse(String openid, String unionid, String sessionKey) {}

    private record WxRawResponse(String openid, String session_key, String unionid, Integer errcode, String errmsg) {}
}
