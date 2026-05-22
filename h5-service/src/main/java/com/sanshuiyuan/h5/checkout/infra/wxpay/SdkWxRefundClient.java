package com.sanshuiyuan.h5.checkout.infra.wxpay;

import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import com.wechat.pay.java.service.refund.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 微信支付 V3 退款真实实现（wechatpay-java RefundService + NotificationParser）。
 */
public class SdkWxRefundClient implements WxRefundClient {

    private static final Logger log = LoggerFactory.getLogger(SdkWxRefundClient.class);

    private final com.wechat.pay.java.service.refund.RefundService refundService;
    private final NotificationParser parser;
    private final String refundNotifyUrl;

    public SdkWxRefundClient(com.wechat.pay.java.service.refund.RefundService refundService,
                             NotificationParser parser, String refundNotifyUrl) {
        this.refundService = refundService;
        this.parser = parser;
        this.refundNotifyUrl = refundNotifyUrl;
    }

    @Override
    public void refund(String outTradeNo, String refundNo, Long amountCents) {
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(outTradeNo);
        request.setOutRefundNo(refundNo);
        if (refundNotifyUrl != null && !refundNotifyUrl.isBlank()) {
            request.setNotifyUrl(refundNotifyUrl);
        }
        AmountReq amount = new AmountReq();
        amount.setRefund(amountCents);
        amount.setTotal(amountCents);
        amount.setCurrency("CNY");
        request.setAmount(amount);
        try {
            refundService.create(request);
        } catch (Exception e) {
            log.error("微信退款发起失败 outTradeNo={} refundNo={}", outTradeNo, refundNo, e);
            throw new BizException(ErrorCode.REFUND_FAILED, "发起退款失败");
        }
    }

    @Override
    public RefundCallbackResult parseCallback(String body, String signature,
                                               String timestamp, String nonce, String serial) {
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serial)
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(body)
                .build();
        try {
            RefundNotification notification = parser.parse(requestParam, RefundNotification.class);
            boolean success = notification.getRefundStatus() == Status.SUCCESS;
            return new RefundCallbackResult(
                    notification.getOutRefundNo(),
                    notification.getRefundId(),
                    success);
        } catch (Exception e) {
            log.warn("微信退款回调验签/解密失败: {}", e.getMessage());
            return null;
        }
    }
}
