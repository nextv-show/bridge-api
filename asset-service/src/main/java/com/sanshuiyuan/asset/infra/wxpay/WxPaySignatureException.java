package com.sanshuiyuan.asset.infra.wxpay;

/**
 * D.2.5: Raised when a WeChat Pay V3 callback fails signature verification (or cannot be
 * decrypted, or the service is not configured to verify). The controller maps this to an
 * HTTP 401 FAIL response.
 */
public class WxPaySignatureException extends RuntimeException {
    public WxPaySignatureException(String message) {
        super(message);
    }

    public WxPaySignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
