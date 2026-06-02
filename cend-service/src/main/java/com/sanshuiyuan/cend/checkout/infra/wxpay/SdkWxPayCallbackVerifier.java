package com.sanshuiyuan.cend.checkout.infra.wxpay;

import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 微信支付 V3 异步回调验签 + 解密（wechatpay-java NotificationParser）。
 * 验签失败/解密失败一律 valid=false，由上层返回 FAIL 拒绝处理。
 */
public class SdkWxPayCallbackVerifier implements WxPayCallbackVerifier {

    private static final Logger log = LoggerFactory.getLogger(SdkWxPayCallbackVerifier.class);

    private final NotificationParser parser;

    public SdkWxPayCallbackVerifier(NotificationParser parser) {
        this.parser = parser;
    }

    @Override
    public VerifyResult verifyAndDecrypt(String body, String signature, String timestamp,
                                          String nonce, String serial) {
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serial)
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(body)
                .build();
        try {
            Transaction tx = parser.parse(requestParam, Transaction.class);
            String tradeState = tx.getTradeState() == null ? null : tx.getTradeState().name();
            return new VerifyResult(true, tx.getTransactionId(), tx.getOutTradeNo(), tradeState, body);
        } catch (Exception e) {
            log.warn("微信支付回调验签/解密失败: {}", e.getMessage());
            return new VerifyResult(false, null, null, null, body);
        }
    }
}
