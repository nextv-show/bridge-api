package com.sanshuiyuan.user.referral;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 关系链相关 Bean 装配。{@link RefIdCodec} 的签名密钥经配置注入（见 application.yml 的
 * {@code user.referral.ref-id-secret}），生产务必用环境变量 {@code USER_REFERRAL_REF_ID_SECRET} 覆盖默认值。
 */
@Configuration
public class ReferralConfig {

    @Bean
    public RefIdCodec refIdCodec(@Value("${user.referral.ref-id-secret}") String secret) {
        return new RefIdCodec(secret);
    }
}
