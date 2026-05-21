package com.sanshuiyuan.h5.checkout.api.dto;

public record KycVerifyResponse(
    String status,
    String realNameMask,
    String idCardMask
) {}
