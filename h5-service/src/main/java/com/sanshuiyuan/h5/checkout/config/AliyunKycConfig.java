package com.sanshuiyuan.h5.checkout.config;

import com.sanshuiyuan.h5.checkout.infra.aliyun.AliyunKycClient;
import com.sanshuiyuan.h5.checkout.infra.aliyun.SdkAliyunKycClient;
import com.sanshuiyuan.h5.checkout.infra.aliyun.StubAliyunKycClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AliyunKycConfig {

    private static final Logger log = LoggerFactory.getLogger(AliyunKycConfig.class);

    /**
     * 配置了真实 AccessKey + 非零 SceneId 时启用真实实人认证，否则回退 stub（本地/CI 联调）。
     */
    @Bean
    public AliyunKycClient aliyunKycClient(
            @Value("${aliyun.kyc.access-key:}") String accessKey,
            @Value("${aliyun.kyc.secret-key:}") String secretKey,
            @Value("${aliyun.kyc.scene-id:0}") long sceneId,
            @Value("${aliyun.kyc.endpoint:cloudauth.aliyuncs.com}") String endpoint,
            @Value("${aliyun.kyc.product-code:LR_FR}") String productCode,
            @Value("${aliyun.kyc.model:MOVE_ACTION}") String model) {
        if (accessKey != null && !accessKey.isBlank() && sceneId > 0) {
            log.info("启用阿里云活体检测 endpoint={} productCode={} model={} sceneId={}",
                    endpoint, productCode, model, sceneId);
            return new SdkAliyunKycClient(accessKey, secretKey, endpoint, sceneId, productCode, model);
        }
        log.warn("阿里云实人认证未配置（access-key/scene-id 缺失），回退 stub 实现");
        return new StubAliyunKycClient();
    }
}
