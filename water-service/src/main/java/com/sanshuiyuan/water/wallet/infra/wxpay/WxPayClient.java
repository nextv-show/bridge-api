package com.sanshuiyuan.water.wallet.infra.wxpay;

/**
 * 微信支付 V3 JSAPI 下单接口（钱包充值）。商户私钥/APIv3 Key 仅后端持有，
 * paySign 由 SDK 用商户私钥签名，前端不接触任何密钥。
 */
public interface WxPayClient {

    PrepayResult jsapiPrepay(String outTradeNo, String openid, Long amountCents, String description);

    record PrepayResult(String prepayId, String appId, String timeStamp, String nonceStr,
                        String packageVal, String signType, String paySign) {}
}
