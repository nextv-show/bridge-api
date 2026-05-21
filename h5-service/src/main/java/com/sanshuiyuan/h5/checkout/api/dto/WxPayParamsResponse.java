package com.sanshuiyuan.h5.checkout.api.dto;

public record WxPayParamsResponse(
    String appId,
    String timeStamp,
    String nonceStr,
    String package_,
    String signType,
    String paySign
) {}
