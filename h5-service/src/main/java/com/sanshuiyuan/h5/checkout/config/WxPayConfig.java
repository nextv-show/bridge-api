package com.sanshuiyuan.h5.checkout.config;

import com.sanshuiyuan.h5.checkout.infra.wxpay.StubWxPayClient;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WxPayConfig {

    // TODO: When real credentials are configured, create SdkWxPayClient bean
    // @Bean
    // @ConditionalOnProperty(prefix = "wxpay", name = "api-v3-key")
    // public WxPayClient sdkWxPayClient() { return new SdkWxPayClient(...); }

    @Bean
    @ConditionalOnMissingBean(WxPayClient.class)
    public WxPayClient stubWxPayClient() {
        return new StubWxPayClient();
    }
}
