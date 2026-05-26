package com.sanshuiyuan.asset.infra.wxpay;

import java.util.Map;

/** dev/CI 无凭证时的回退：钱包回调一律失败关闭，绝不放行未验签的入账。 */
public class UnconfiguredWalletPayCallbackVerifier implements WalletPayCallbackVerifier {
    @Override
    public WalletCallbackResult verify(Map<String, String> headers, String body) {
        throw new WxPaySignatureException("wxpay not configured; wallet callback rejected");
    }
}
