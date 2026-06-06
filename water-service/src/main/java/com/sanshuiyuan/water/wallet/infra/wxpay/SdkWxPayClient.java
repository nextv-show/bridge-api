package com.sanshuiyuan.water.wallet.infra.wxpay;

import com.sanshuiyuan.water.common.BizException;
import com.sanshuiyuan.water.common.ErrorCode;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 微信支付 V3 JSAPI 真实实现（wechatpay-java SDK）。
 * 商户私钥 / APIv3 Key 仅在后端 Config 内持有；paySign 由 SDK 用商户私钥签名，前端不接触任何密钥。
 */
public class SdkWxPayClient implements WxPayClient {

    private static final Logger log = LoggerFactory.getLogger(SdkWxPayClient.class);

    private final JsapiServiceExtension jsapiService;
    private final String appId;
    private final String mchId;
    private final String notifyUrl;

    public SdkWxPayClient(JsapiServiceExtension jsapiService, String appId, String mchId, String notifyUrl) {
        this.jsapiService = jsapiService;
        this.appId = appId;
        this.mchId = mchId;
        this.notifyUrl = notifyUrl;
    }

    @Override
    public PrepayResult jsapiPrepay(String outTradeNo, String openid, Long amountCents, String description) {
        PrepayRequest request = new PrepayRequest();
        request.setAppid(appId);
        request.setMchid(mchId);
        request.setDescription(description);
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(notifyUrl);

        Amount amount = new Amount();
        amount.setTotal(amountCents.intValue());
        amount.setCurrency("CNY");
        request.setAmount(amount);

        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);

        log.info("微信 JSAPI 充值下单 outTradeNo={} notifyUrl={}", outTradeNo, notifyUrl);
        try {
            PrepayWithRequestPaymentResponse resp = jsapiService.prepayWithRequestPayment(request);
            String packageVal = resp.getPackageVal();
            String prepayId = packageVal != null && packageVal.startsWith("prepay_id=")
                    ? packageVal.substring("prepay_id=".length()) : packageVal;
            return new PrepayResult(
                    prepayId,
                    resp.getAppId(),
                    resp.getTimeStamp(),
                    resp.getNonceStr(),
                    packageVal,
                    resp.getSignType(),
                    resp.getPaySign());
        } catch (Exception e) {
            log.error("微信 JSAPI 充值下单失败 outTradeNo={} errType={} errMsg={}",
                    outTradeNo, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new BizException(ErrorCode.PAY_PREPAY_FAILED, "发起微信支付失败");
        }
    }
}
