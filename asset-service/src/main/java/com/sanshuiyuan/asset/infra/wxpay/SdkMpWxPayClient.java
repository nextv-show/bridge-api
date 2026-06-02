package com.sanshuiyuan.asset.infra.wxpay;

import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 小程序 JSAPI 真实实现（wechatpay-java SDK）。appId 必须为小程序 appid（非公众号），
 * payer.openid 为小程序 openid。其余商户凭证与公众号支付共用。
 */
public class SdkMpWxPayClient implements MpWxPayClient {

    private static final Logger log = LoggerFactory.getLogger(SdkMpWxPayClient.class);

    private final JsapiServiceExtension jsapiService;
    private final String mpAppId;
    private final String mchId;
    private final String notifyUrl;

    public SdkMpWxPayClient(JsapiServiceExtension jsapiService, String mpAppId, String mchId, String notifyUrl) {
        this.jsapiService = jsapiService;
        this.mpAppId = mpAppId;
        this.mchId = mchId;
        this.notifyUrl = notifyUrl;
    }

    @Override
    public MpPrepayResult jsapiPrepay(String outTradeNo, String openid, long amountCents, String description) {
        PrepayRequest request = new PrepayRequest();
        request.setAppid(mpAppId);
        request.setMchid(mchId);
        request.setDescription(description);
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(notifyUrl);

        Amount amount = new Amount();
        amount.setTotal((int) amountCents);
        amount.setCurrency("CNY");
        request.setAmount(amount);

        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);

        try {
            PrepayWithRequestPaymentResponse resp = jsapiService.prepayWithRequestPayment(request);
            return new MpPrepayResult(
                    resp.getAppId(),
                    resp.getTimeStamp(),
                    resp.getNonceStr(),
                    resp.getPackageVal(),
                    resp.getSignType(),
                    resp.getPaySign());
        } catch (Exception e) {
            log.error("小程序 JSAPI 统一下单失败 outTradeNo={} err={}", outTradeNo, e.getMessage(), e);
            throw new IllegalStateException("发起微信支付失败");
        }
    }

    @Override
    public TradeQueryResult queryOrder(String outTradeNo) {
        try {
            QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
            request.setMchid(mchId);
            request.setOutTradeNo(outTradeNo);
            Transaction txn = jsapiService.queryOrderByOutTradeNo(request);
            String tradeState = txn.getTradeState() == null ? null : txn.getTradeState().name();
            if (!"SUCCESS".equals(tradeState)) {
                return new TradeQueryResult(tradeState, null, null);
            }
            LocalDateTime successTime = null;
            String st = txn.getSuccessTime();
            if (st != null && !st.isBlank()) {
                try {
                    // 微信 successTime 为 RFC3339（如 2026-05-28T08:17:19+08:00），转本地时区时间。
                    successTime = OffsetDateTime.parse(st).toLocalDateTime();
                } catch (Exception parseEx) {
                    log.warn("解析微信 successTime 失败 outTradeNo={} successTime={}", outTradeNo, st);
                }
            }
            return new TradeQueryResult(tradeState, txn.getTransactionId(), successTime);
        } catch (Exception e) {
            // 查单失败不抛出，避免中断对账批处理。
            log.error("小程序微信主动查单失败 outTradeNo={} errType={} errMsg={}",
                    outTradeNo, e.getClass().getSimpleName(), e.getMessage());
            return new TradeQueryResult("QUERY_ERROR", null, null);
        }
    }

    @Override
    public boolean isReal() {
        return true;
    }
}
