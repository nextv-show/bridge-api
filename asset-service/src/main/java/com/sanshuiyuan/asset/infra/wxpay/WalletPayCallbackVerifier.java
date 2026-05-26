package com.sanshuiyuan.asset.infra.wxpay;

import java.util.Map;

/**
 * 钱包充值的微信支付 V3 回调验签+解密。与设备订单回调（{@link WxPayCallbackVerifier}，
 * 把 out_trade_no 解析为 Long orderId）不同：钱包 out_trade_no 带 "WR" 前缀，故单独返回字符串。
 */
public interface WalletPayCallbackVerifier {
    WalletCallbackResult verify(Map<String, String> headers, String body);
}
