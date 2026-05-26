package com.sanshuiyuan.asset.infra.wxpay;

/**
 * 小程序微信支付 V3 JSAPI 下单。商户私钥/APIv3 Key 仅后端持有，paySign 由 SDK 用商户私钥签名。
 * 配齐凭证时为 {@link SdkMpWxPayClient}，否则回退 {@link StubMpWxPayClient}（dev/CI）。
 */
public interface MpWxPayClient {
    MpPrepayResult jsapiPrepay(String outTradeNo, String openid, long amountCents, String description);

    /** 是否为真实 SDK 实现（false 表示 stub，前端应走 dev 模拟支付而非真实 requestPayment）。 */
    boolean isReal();
}
