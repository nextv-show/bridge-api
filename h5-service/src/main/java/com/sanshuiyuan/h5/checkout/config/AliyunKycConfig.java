package com.sanshuiyuan.h5.checkout.config;

import com.sanshuiyuan.h5.checkout.infra.aliyun.AliyunKycClient;
import com.sanshuiyuan.h5.checkout.infra.aliyun.SdkAliyunKycClient;
import com.sanshuiyuan.h5.checkout.infra.aliyun.StubAliyunKycClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AliyunKycConfig {

    @Bean
    @ConditionalOnProperty(prefix = "aliyun.kyc", name = "access-key")
    public AliyunKycClient sdkAliyunKycClient() {
        return new SdkAliyunKycClient();
    }

    @Bean
    @ConditionalOnMissingBean(AliyunKycClient.class)
    public AliyunKycClient stubAliyunKycClient() {
        return new StubAliyunKycClient();
    }
}
