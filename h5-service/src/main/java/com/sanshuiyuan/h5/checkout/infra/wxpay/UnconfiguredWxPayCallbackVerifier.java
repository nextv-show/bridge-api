package com.sanshuiyuan.h5.checkout.infra.wxpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev fallback: always rejects real callbacks (safe-fail).
 * Use /pay/simulate-callback for happy-path testing.
 */
public class UnconfiguredWxPayCallbackVerifier implements WxPayCallbackVerifier {

    private static final Logger log = LoggerFactory.getLogger(UnconfiguredWxPayCallbackVerifier.class);

    @Override
    public VerifyResult verifyAndDecrypt(String body, String signature, String timestamp,
                                          String nonce, String serial) {
        log.warn("WxPay callback verifier not configured — rejecting callback");
        return new VerifyResult(false, null, null, null, body);
    }
}
