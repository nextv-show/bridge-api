package com.sanshuiyuan.h5.checkout.api.dto;

import java.util.Map;

/**
 * 小程序 KYC 初始化响应。已实名则 {@code alreadyVerified=true} 并回脱敏信息；
 * 否则回 {@code certifyId} + 小程序端人脸 SDK 所需 {@code sdkParams}。
 */
public record MiniKycInitResponse(
        String certifyId,
        Map<String, Object> sdkParams,
        Boolean alreadyVerified,
        String realNameMask,
        String idCardMask,
        String phoneMask
) {
    public static MiniKycInitResponse init(String certifyId, Map<String, Object> sdkParams) {
        return new MiniKycInitResponse(certifyId, sdkParams, false, null, null, null);
    }

    public static MiniKycInitResponse alreadyVerified(String realNameMask, String idCardMask, String phoneMask) {
        return new MiniKycInitResponse(null, null, true, realNameMask, idCardMask, phoneMask);
    }
}
