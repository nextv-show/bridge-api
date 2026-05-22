package com.sanshuiyuan.h5.checkout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 调起微信支付控件所需参数。注意 package 是 Java 关键字，用 package_ 承载，
 * 但 JSON 序列化键必须为 "package"（WeixinJSBridge / wx.requestPayment 约定）。
 */
public record WxPayParamsResponse(
    String appId,
    String timeStamp,
    String nonceStr,
    @JsonProperty("package") String package_,
    String signType,
    String paySign
) {}
