package com.sanshuiyuan.asset.infra.wxpay;

import java.util.Map;

/**
 * D.2.5: Testable seam over WeChat Pay V3 callback verification + decryption.
 * Implementations verify the request signature using the four
 * {@code Wechatpay-Signature/Timestamp/Nonce/Serial} headers and decrypt the body.
 *
 * @see SdkWxPayCallbackVerifier real implementation, active when wxpay credentials are present
 * @see UnconfiguredWxPayCallbackVerifier dev fallback, fails closed when no credentials are set
 */
public interface WxPayCallbackVerifier {

    /**
     * Verifies and decrypts a WeChat Pay V3 callback.
     *
     * @param headers request headers (case-insensitive lookup is the caller's concern)
     * @param body    raw request body
     * @return the verified, decrypted callback
     * @throws WxPaySignatureException if verification fails or the service is not configured
     */
    VerifiedCallback verify(Map<String, String> headers, String body);
}
