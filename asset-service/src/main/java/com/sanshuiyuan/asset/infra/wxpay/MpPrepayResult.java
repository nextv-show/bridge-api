package com.sanshuiyuan.asset.infra.wxpay;

/**
 * 小程序 JSAPI 下单结果（透传给 Taro.requestPayment）。
 * package_ 的 JSON key 为 "package"（小程序 requestPayment 字段名）。
 */
public record MpPrepayResult(
    String appId,
    String timeStamp,
    String nonceStr,
    String packageVal,
    String signType,
    String paySign
) {}
