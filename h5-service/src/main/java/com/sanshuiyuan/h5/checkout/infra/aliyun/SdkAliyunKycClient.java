package com.sanshuiyuan.h5.checkout.infra.aliyun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 阿里云金融实人认证真实实现。
 * 完整 SDK 调用需配置 aliyun.kyc.access-key / secret-key / scene-id，
 * 当前为可编译骨架，待联调时替换为真实 API 调用。
 */
public class SdkAliyunKycClient implements AliyunKycClient {

    private static final Logger log = LoggerFactory.getLogger(SdkAliyunKycClient.class);

    @Override
    public KycInitResult init(String openid) {
        // TODO: 调用阿里云 InitFaceVerify API
        String certifyId = "sdk-" + UUID.randomUUID().toString().substring(0, 8);
        return new KycInitResult(certifyId, "sdk-token", "https://cloudauth.aliyun.com/" + certifyId);
    }

    @Override
    public KycVerifyResult queryResult(String certifyId) {
        // TODO: 调用阿里云 DescribeFaceVerify API
        return new KycVerifyResult(true, "", "");
    }
}
