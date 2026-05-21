package com.sanshuiyuan.h5.checkout.infra.aliyun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class StubAliyunKycClient implements AliyunKycClient {

    private static final Logger log = LoggerFactory.getLogger(StubAliyunKycClient.class);

    @Override
    public KycInitResult init(String openid) {
        String certifyId = "stub-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[stub] KYC init for openid=*** certifyId={}", certifyId);
        return new KycInitResult(certifyId, "stub-token", "https://stub-verify.example.com/" + certifyId);
    }

    @Override
    public KycVerifyResult queryResult(String certifyId) {
        log.info("[stub] KYC queryResult certifyId={}", certifyId);
        return new KycVerifyResult(true, "张三", "340123199001011234");
    }
}
