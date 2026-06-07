package com.sanshuiyuan.water.wallet.infra.wxpay;

/**
 * 微信支付 V3 异步回调验签 + 解密。验签/解密失败一律 valid=false，由上层返回 FAIL 拒绝处理。
 */
public interface WxPayCallbackVerifier {

    VerifyResult verifyAndDecrypt(String body, String signature, String timestamp,
                                   String nonce, String serial);

    record VerifyResult(boolean valid, String transactionId, String outTradeNo,
                        String tradeState, String rawBody) {}
}
