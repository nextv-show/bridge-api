package com.sanshuiyuan.h5.checkout.infra.wxpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real WeChat Pay V3 callback verification using wechatpay-java NotificationParser.
 * Activated when wxpay.api-v3-key is configured.
 * Current implementation is a skeleton — replace with real SDK calls when credentials are available.
 */
public class SdkWxPayCallbackVerifier implements WxPayCallbackVerifier {

    private static final Logger log = LoggerFactory.getLogger(SdkWxPayCallbackVerifier.class);

    @Override
    public VerifyResult verifyAndDecrypt(String body, String signature, String timestamp,
                                          String nonce, String serial) {
        // TODO: Use NotificationParser to verify signature and decrypt body
        log.info("SDK callback verification — stub implementation");
        return new VerifyResult(false, null, null, null, body);
    }
}
