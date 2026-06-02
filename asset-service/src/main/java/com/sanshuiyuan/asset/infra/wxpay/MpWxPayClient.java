package com.sanshuiyuan.asset.infra.wxpay;

/**
 * 小程序微信支付 V3 JSAPI 下单。商户私钥/APIv3 Key 仅后端持有，paySign 由 SDK 用商户私钥签名。
 * 配齐凭证时为 {@link SdkMpWxPayClient}，否则回退 {@link StubMpWxPayClient}（dev/CI）。
 */
public interface MpWxPayClient {
    MpPrepayResult jsapiPrepay(String outTradeNo, String openid, long amountCents, String description);

    /**
     * 主动查单（按商户订单号）。微信回调不达时由对账任务调用。
     * 任何异常都不抛出，返回 tradeState="QUERY_ERROR"，绝不中断批处理。
     */
    TradeQueryResult queryOrder(String outTradeNo);

    /** 是否为真实 SDK 实现（false 表示 stub，前端应走 dev 模拟支付而非真实 requestPayment）。 */
    boolean isReal();

    /**
     * 查单结果。tradeState 取微信枚举名（SUCCESS/NOTPAY/CLOSED/REFUND/PAYERROR/USERPAYING/REVOKED/ACCEPT），
     * 或本地查询失败时为 "QUERY_ERROR"、stub 时为 "STUB"。transactionId/successTime 仅在 SUCCESS 时有值。
     */
    record TradeQueryResult(String tradeState, String transactionId, java.time.LocalDateTime successTime) {}
}
