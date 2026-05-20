package com.sanshuiyuan.asset.infra.wxpay;

import java.util.Map;

/**
 * D.2.5: Dev/CI fallback verifier, registered (via {@link WxPayVerifierConfig}, guarded by
 * {@code @ConditionalOnMissingBean(WxPayCallbackVerifier.class)}) whenever no
 * {@link SdkWxPayCallbackVerifier} was created because wxpay credentials are absent.
 *
 * <p>It fails closed: any real callback is rejected with {@link WxPaySignatureException}. Without
 * the merchant api-v3 key there is no way to verify a genuine WeChat signature, so accepting a
 * callback here would be a security hole. The happy path in dev goes through the
 * {@code /wxpay/simulate-callback} tool endpoint instead.
 */
public class UnconfiguredWxPayCallbackVerifier implements WxPayCallbackVerifier {

    @Override
    public VerifiedCallback verify(Map<String, String> headers, String body) {
        throw new WxPaySignatureException("wxpay not configured");
    }
}
