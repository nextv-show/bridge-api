package com.sanshuiyuan.cend.config;

import com.sanshuiyuan.cend.referral.HttpWxMiniCodeClient;
import com.sanshuiyuan.cend.referral.StubWxMiniCodeClient;
import com.sanshuiyuan.cend.referral.WxMiniCodeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信小程序码相关 Bean 配置。
 * 复用 wx.miniprogram.app-id / app-secret（与小程序登录同凭证）。
 * 当 app-secret 为空时创建 stub 实现，避免启动失败。
 */
@Configuration
public class WxMiniCodeConfig {

    private static final Logger log = LoggerFactory.getLogger(WxMiniCodeConfig.class);

    @Bean
    public WxMiniCodeClient wxMiniCodeClient(
            @Value("${wx.miniprogram.app-id:stub}") String appId,
            @Value("${wx.miniprogram.app-secret:}") String appSecret) {
        if (appSecret != null && !appSecret.isBlank() && !"stub".equals(appSecret)) {
            log.info("创建 HttpWxMiniCodeClient appId={}", maskAppId(appId));
            return new HttpWxMiniCodeClient(appId, appSecret);
        }
        log.info("小程序 app-secret 未配置，使用 StubWxMiniCodeClient");
        return new StubWxMiniCodeClient();
    }

    private static String maskAppId(String appId) {
        if (appId == null || appId.length() <= 4) {
            return "****";
        }
        return appId.substring(0, 4) + "****";
    }
}
