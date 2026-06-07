package com.sanshuiyuan.water.wallet.infra.wxpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev fallback: 微信支付未配置时一律拒绝真实回调（安全失败）。
 * 联调走 /api/w/wallet/topup/simulate-callback 验证支付成功路径。
 */
public class UnconfiguredWxPayCallbackVerifier implements WxPayCallbackVerifier {

    private static final Logger log = LoggerFactory.getLogger(UnconfiguredWxPayCallbackVerifier.class);

    @Override
    public VerifyResult verifyAndDecrypt(String body, String signature, String timestamp,
                                          String nonce, String serial) {
        log.warn("微信支付回调验签器未配置 — 拒绝回调");
        return new VerifyResult(false, null, null, null, body);
    }
}
