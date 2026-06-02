package com.sanshuiyuan.cend.checkout.infra.wxpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class StubWxPayClient implements WxPayClient {

    private static final Logger log = LoggerFactory.getLogger(StubWxPayClient.class);

    @Override
    public PrepayResult jsapiPrepay(String outTradeNo, String openid, Long amountCents, String description) {
        log.info("[stub] JSAPI prepay outTradeNo={} amount={}", outTradeNo, amountCents);
        return new PrepayResult(
            "wx" + UUID.randomUUID().toString().substring(0, 24),
            "wx-stub-appid",
            String.valueOf(System.currentTimeMillis() / 1000),
            UUID.randomUUID().toString().substring(0, 32),
            "prepay_id=wx_stub_" + outTradeNo,
            "RSA",
            "stub-pay-sign-" + UUID.randomUUID().toString().substring(0, 16)
        );
    }

    @Override
    public TradeQueryResult queryOrder(String outTradeNo) {
        // stub 环境永不误判已支付。
        log.info("[stub] queryOrder outTradeNo={} -> NOTPAY", outTradeNo);
        return new TradeQueryResult("NOTPAY", null, null);
    }

    @Override
    public void closeOrder(String outTradeNo) {
        log.info("[stub] closeOrder outTradeNo={}", outTradeNo);
    }
}
