package com.sanshuiyuan.water.wallet.infra.wxpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/** Dev stub：返回假的预支付参数，用于本地联调（配合 simulate-callback 走支付成功路径）。 */
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
}
