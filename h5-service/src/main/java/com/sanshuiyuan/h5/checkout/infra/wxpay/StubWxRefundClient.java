package com.sanshuiyuan.h5.checkout.infra.wxpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StubWxRefundClient implements WxRefundClient {

    private static final Logger log = LoggerFactory.getLogger(StubWxRefundClient.class);

    @Override
    public void refund(String outTradeNo, String refundNo, Long amountCents) {
        log.info("[stub] 退款 outTradeNo={} refundNo={} amount={}", outTradeNo, refundNo, amountCents);
    }

    @Override
    public RefundCallbackResult parseCallback(String body, String signature,
                                               String timestamp, String nonce, String serial) {
        log.info("[stub] 退款回调解析");
        // Stub: return null to indicate no real parsing in dev
        // In production, this decrypts the WeChat callback body
        return null;
    }

    @Override
    public RefundCallbackResult queryRefund(String refundNo) {
        log.info("[stub] 退款查询 refundNo={}（stub 始终视为处理中）", refundNo);
        return null;
    }
}
