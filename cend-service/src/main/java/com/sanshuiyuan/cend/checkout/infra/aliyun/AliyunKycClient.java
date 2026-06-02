package com.sanshuiyuan.cend.checkout.infra.aliyun;

public interface AliyunKycClient {

    /**
     * 发起金融级实人认证（InitFaceVerify）。
     *
     * @param openid    业务用户标识（用于拼 OuterOrderNo / 追踪）
     * @param metaInfo  前端 getMetaInfo() 采集的设备指纹（不可硬编码，否则拿不到可用 CertifyUrl）
     * @param returnUrl 认证完成后阿里云回跳的 H5 地址
     * @return certifyId + 可直接跳转的 CertifyUrl（放在 verifyUrl 字段）
     */
    KycInitResult init(String openid, String metaInfo, String returnUrl);

    /** 查询认证结果（DescribeFaceVerify）。 */
    KycVerifyResult queryResult(String certifyId);

    /** verifyToken 兼容旧契约（恒 null）；verifyUrl 即阿里云 CertifyUrl。 */
    record KycInitResult(String certifyId, String verifyToken, String verifyUrl) {}

    record KycVerifyResult(boolean passed, String realName, String idCardNo) {}
}
