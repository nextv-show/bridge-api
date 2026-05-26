package com.sanshuiyuan.asset.infra.wxpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * dev/CI 占位：未配置微信支付凭证时返回 stub 下单参数。前端据 isReal()=false 走 dev 模拟支付，
 * 不会用这些参数真正调起 requestPayment。
 */
public class StubMpWxPayClient implements MpWxPayClient {

    private static final Logger log = LoggerFactory.getLogger(StubMpWxPayClient.class);

    @Override
    public MpPrepayResult jsapiPrepay(String outTradeNo, String openid, long amountCents, String description) {
        log.info("[stub] 小程序 JSAPI prepay outTradeNo={} amount={}", outTradeNo, amountCents);
        return new MpPrepayResult(
                "stub-appid",
                String.valueOf(System.currentTimeMillis() / 1000),
                "stubnonce",
                "prepay_id=wx_stub_" + outTradeNo,
                "RSA",
                "stub-paysign");
    }

    @Override
    public boolean isReal() {
        return false;
    }
}
