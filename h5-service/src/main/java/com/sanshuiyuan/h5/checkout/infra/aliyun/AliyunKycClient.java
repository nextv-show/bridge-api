package com.sanshuiyuan.h5.checkout.infra.aliyun;

public interface AliyunKycClient {

    KycInitResult init(String openid);

    KycVerifyResult queryResult(String certifyId);

    record KycInitResult(String certifyId, String verifyToken, String verifyUrl) {}

    record KycVerifyResult(boolean passed, String realName, String idCardNo) {}
}
