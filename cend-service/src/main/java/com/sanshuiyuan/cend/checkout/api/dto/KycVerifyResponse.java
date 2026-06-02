package com.sanshuiyuan.cend.checkout.api.dto;

public record KycVerifyResponse(
    String status,
    String realNameMask,
    String idCardMask,
    String phoneMask
) {
    /** Compat: 旧版不返回 phoneMask */
    public KycVerifyResponse(String status, String realNameMask, String idCardMask) {
        this(status, realNameMask, idCardMask, null);
    }
}
