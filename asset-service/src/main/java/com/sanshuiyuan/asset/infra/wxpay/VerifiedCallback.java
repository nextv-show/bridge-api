package com.sanshuiyuan.asset.infra.wxpay;

/**
 * D.2.5: Result of a successfully verified + decrypted WeChat Pay V3 callback.
 *
 * @param transactionId WeChat Pay transaction id
 * @param orderId       our order id, parsed from {@code out_trade_no}
 */
public record VerifiedCallback(String transactionId, Long orderId) {
}
