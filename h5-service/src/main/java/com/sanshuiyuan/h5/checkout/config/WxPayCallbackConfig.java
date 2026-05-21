package com.sanshuiyuan.h5.checkout.config;

import com.sanshuiyuan.h5.checkout.infra.wxpay.SdkWxPayCallbackVerifier;
import com.sanshuiyuan.h5.checkout.infra.wxpay.UnconfiguredWxPayCallbackVerifier;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayCallbackVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WxPayCallbackConfig {

    @Bean
    @ConditionalOnProperty(prefix = "wxpay", name = "api-v3-key")
    public WxPayCallbackVerifier sdkWxPayCallbackVerifier() {
        return new SdkWxPayCallbackVerifier();
    }

    @Bean
    @ConditionalOnMissingBean(WxPayCallbackVerifier.class)
    public WxPayCallbackVerifier unconfiguredWxPayCallbackVerifier() {
        return new UnconfiguredWxPayCallbackVerifier();
    }
}
