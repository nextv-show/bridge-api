package com.sanshuiyuan.h5.checkout.config;

import com.sanshuiyuan.h5.checkout.infra.minikyc.MiniKycProvider;
import com.sanshuiyuan.h5.checkout.infra.minikyc.StubMiniKycProvider;
import com.sanshuiyuan.h5.checkout.infra.minikyc.TencentFaceIdMiniKycProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MiniKycConfig {

    private static final Logger log = LoggerFactory.getLogger(MiniKycConfig.class);

    /**
     * 配置了真实腾讯人脸核身凭证（secret-id 非空）时启用腾讯通道，否则回退 stub（本地/CI/未对接）。
     */
    @Bean
    public MiniKycProvider miniKycProvider(
            @Value("${tencent.faceid.secret-id:}") String secretId,
            @Value("${tencent.faceid.secret-key:}") String secretKey,
            @Value("${tencent.faceid.rule-id:}") String ruleId,
            @Value("${tencent.faceid.region:ap-guangzhou}") String region,
            @Value("${tencent.faceid.mp-app-id:}") String mpAppId) {
        if (secretId != null && !secretId.isBlank()) {
            log.info("启用腾讯人脸核身（小程序通道）region={} ruleId={}", region, ruleId);
            return new TencentFaceIdMiniKycProvider(secretId, secretKey, ruleId, region, mpAppId);
        }
        log.warn("小程序人脸核身未配置（tencent.faceid.secret-id 缺失），回退 stub 实现");
        return new StubMiniKycProvider();
    }
}
