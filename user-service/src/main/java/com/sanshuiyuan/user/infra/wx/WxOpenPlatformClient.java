package com.sanshuiyuan.user.infra.wx;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WxOpenPlatformClient {

    private final RestTemplate restTemplate;
    private final String appId;
    private final String appSecret;

    public WxOpenPlatformClient(
            RestTemplate restTemplate,
            @Value("${wx.open-platform.app-id}") String appId,
            @Value("${wx.open-platform.app-secret}") String appSecret) {
        this.restTemplate = restTemplate;
        this.appId = appId;
        this.appSecret = appSecret;
    }

    public WxOAuthResponse exchangeAppCode(String wxAuthCode) {
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid={appId}&secret={appSecret}&code={code}&grant_type=authorization_code";
        var response = restTemplate.getForObject(url, WxRawResponse.class,
                java.util.Map.of("appId", appId, "appSecret", appSecret, "code", wxAuthCode));
        if (response == null) {
            throw new RuntimeException("Empty response from WeChat API");
        }
        if (response.errcode() != null && response.errcode() != 0) {
            throw new RuntimeException("WeChat API error: " + response.errcode() + " - " + response.errmsg());
        }
        return new WxOAuthResponse(response.openid(), response.unionid(), response.access_token());
    }

    public record WxOAuthResponse(String openid, String unionid, String accessToken) {}

    private record WxRawResponse(String access_token, String openid, String unionid, Integer errcode, String errmsg) {}
}
