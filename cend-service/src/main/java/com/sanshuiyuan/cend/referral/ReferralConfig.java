package com.sanshuiyuan.cend.referral;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 关系链相关 Bean 装配。{@link RefIdCodec} 的签名密钥经配置注入（见 application.yml 的
 * {@code h5.referral.ref-id-secret}），生产务必用环境变量覆盖默认值。
 */
@Configuration
public class ReferralConfig {

    @Bean
    public RefIdCodec refIdCodec(@Value("${h5.referral.ref-id-secret}") String secret) {
        return new RefIdCodec(secret);
    }
}
