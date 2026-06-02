package com.sanshuiyuan.cend.checkout.infra.wxpay;

public interface WxPayCallbackVerifier {

    VerifyResult verifyAndDecrypt(String body, String signature, String timestamp,
                                   String nonce, String serial);

    record VerifyResult(boolean valid, String transactionId, String outTradeNo,
                        String tradeState, String rawBody) {}
}
