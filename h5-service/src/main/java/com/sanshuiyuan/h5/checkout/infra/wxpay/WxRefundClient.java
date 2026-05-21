package com.sanshuiyuan.h5.checkout.infra.wxpay;

public interface WxRefundClient {

    void refund(String outTradeNo, String refundNo, Long amountCents);

    RefundCallbackResult parseCallback(String body, String signature,
                                        String timestamp, String nonce, String serial);

    record RefundCallbackResult(String refundNo, String wxRefundId, boolean success) {}
}
