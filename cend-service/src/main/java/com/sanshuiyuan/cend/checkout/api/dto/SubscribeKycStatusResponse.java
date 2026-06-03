package com.sanshuiyuan.cend.checkout.api.dto;

public record SubscribeKycStatusResponse(
        boolean passed,
        String realNameMask,
        String idCardMask,
        String phoneMask
) {
}
