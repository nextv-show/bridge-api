package com.sanshuiyuan.h5.checkout.infra.wxpay;

public interface WxPayClient {

    PrepayResult jsapiPrepay(String outTradeNo, String openid, Long amountCents, String description);

    void closeOrder(String outTradeNo);

    /**
     * 主动查单（按商户订单号）。微信回调不达时由对账任务调用。
     * 任何异常都不抛出，返回 tradeState="QUERY_ERROR"，绝不中断批处理。
     */
    TradeQueryResult queryOrder(String outTradeNo);

    record PrepayResult(String prepayId, String appId, String timeStamp, String nonceStr,
                        String packageVal, String signType, String paySign) {}

    /**
     * 查单结果。tradeState 取微信枚举名（SUCCESS/NOTPAY/CLOSED/REFUND/PAYERROR/USERPAYING/REVOKED/ACCEPT），
     * 或本地查询失败时为 "QUERY_ERROR"。transactionId/successTime 仅在 SUCCESS 时有值。
     */
    record TradeQueryResult(String tradeState, String transactionId, java.time.LocalDateTime successTime) {}
}
