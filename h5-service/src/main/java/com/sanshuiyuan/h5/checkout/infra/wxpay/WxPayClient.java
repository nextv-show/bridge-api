package com.sanshuiyuan.h5.checkout.infra.wxpay;

public interface WxPayClient {

    PrepayResult jsapiPrepay(String outTradeNo, String openid, Long amountCents, String description);

    void closeOrder(String outTradeNo);

    record PrepayResult(String prepayId, String appId, String timeStamp, String nonceStr,
                        String packageVal, String signType, String paySign) {}
}
