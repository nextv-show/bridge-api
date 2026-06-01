package com.sanshuiyuan.h5.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    @Bean
    public H5JwtService h5JwtService(
            @Value("${h5.jwt-secret}") String secret,
            @Value("${h5.jwt-ttl-hours:72}") int ttlHours) {
        return new H5JwtService(secret, ttlHours);
    }

    /**
     * 配置了真实公众号 appSecret（非空且非占位 "stub"）时启用真实网页授权，否则回退 stub。
     */
    @Bean
    public WxAuthClient wxAuthClient(
            @Value("${wxpay.app-id:stub}") String appId,
            @Value("${wxpay.app-secret:}") String appSecret) {
        if (appSecret != null && !appSecret.isBlank() && !"stub".equals(appSecret)) {
            return new HttpWxAuthClient(appId, appSecret);
        }
        return new StubWxAuthClient();
    }

    /**
     * 配置了真实小程序 appSecret（非空且非占位 "stub"）时启用真实 jscode2session，否则回退 stub。
     */
    @Bean
    public WxMiniAuthClient wxMiniAuthClient(
            @Value("${wx.miniprogram.app-id:stub}") String appId,
            @Value("${wx.miniprogram.app-secret:}") String appSecret) {
        if (appSecret != null && !appSecret.isBlank() && !"stub".equals(appSecret)) {
            return new HttpWxMiniAuthClient(appId, appSecret);
        }
        return new StubWxMiniAuthClient();
    }
}
