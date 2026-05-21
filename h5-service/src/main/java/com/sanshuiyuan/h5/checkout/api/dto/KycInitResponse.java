package com.sanshuiyuan.h5.checkout.api.dto;

public record KycInitResponse(
    String certifyId,
    String verifyToken,
    String verifyUrl,
    Boolean alreadyVerified,
    String realNameMask,
    String idCardMask
) {
    public static KycInitResponse init(String certifyId, String verifyToken, String verifyUrl) {
        return new KycInitResponse(certifyId, verifyToken, verifyUrl, false, null, null);
    }

    public static KycInitResponse alreadyVerified(String realNameMask, String idCardMask) {
        return new KycInitResponse(null, null, null, true, realNameMask, idCardMask);
    }
}
