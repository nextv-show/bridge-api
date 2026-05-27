package com.sanshuiyuan.asset.infra.wxpay;

import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

/**
 * 钱包充值回调真实验签器（wechatpay-java），仅当 wxpay.api-v3-key 存在时装配。
 * 复用与设备回调相同的商户凭证；返回 out_trade_no 字符串 + tradeState。
 */
public class SdkWalletPayCallbackVerifier implements WalletPayCallbackVerifier {

    private static final Logger log = LoggerFactory.getLogger(SdkWalletPayCallbackVerifier.class);

    private final NotificationParser parser;

    // parser 由 MpWxPayConfig 用共享 coreConfig（公钥模式或自动证书模式）构建
    public SdkWalletPayCallbackVerifier(NotificationParser parser) {
        this.parser = parser;
    }

    @Override
    public WalletCallbackResult verify(Map<String, String> headers, String body) {
        Map<String, String> ci = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ci.putAll(headers);
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(ci.get("Wechatpay-Serial"))
                .nonce(ci.get("Wechatpay-Nonce"))
                .signature(ci.get("Wechatpay-Signature"))
                .timestamp(ci.get("Wechatpay-Timestamp"))
                .body(body)
                .build();
        try {
            Transaction tx = parser.parse(requestParam, Transaction.class);
            return new WalletCallbackResult(
                    tx.getTransactionId(),
                    tx.getOutTradeNo(),
                    tx.getTradeState() != null ? tx.getTradeState().name() : null);
        } catch (WxPaySignatureException e) {
            throw e;
        } catch (Exception e) {
            log.warn("钱包充值回调验签/解密失败: {}", e.getMessage());
            throw new WxPaySignatureException("signature verification failed", e);
        }
    }
}
