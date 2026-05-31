package com.sanshuiyuan.asset.infra.wxpay;

import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import com.wechat.pay.java.service.refund.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 微信支付 V3 退款真实实现（wechatpay-java RefundService + NotificationParser）。
 * 从 h5-service 移植，去掉 BizException/ErrorCode 依赖，失败抛普通 RuntimeException（匹配 asset 风格）。
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
            throw new RuntimeException("发起退款失败", e);
        }
    }

    @Override
    public RefundCallbackResult queryRefund(String refundNo) {
        QueryByOutRefundNoRequest req = new QueryByOutRefundNoRequest();
        req.setOutRefundNo(refundNo);
        try {
            Refund r = refundService.queryByOutRefundNo(req);
            Status s = r.getStatus();
            if (s == Status.SUCCESS) {
                return new RefundCallbackResult(r.getOutRefundNo(), r.getRefundId(), true);
            }
            if (s == Status.CLOSED) {
                // 退款已关闭（用户/商户撤销），资金未实退；上层据此回滚订单为 PAID。
                return new RefundCallbackResult(r.getOutRefundNo(), r.getRefundId(), false);
            }
            // PROCESSING / ABNORMAL / null：不下结论，等待下次轮询或人工介入。
            if (s == Status.ABNORMAL) {
                log.warn("微信退款 ABNORMAL（可能需要人工介入）refundNo={} refundId={}",
                        refundNo, r.getRefundId());
            } else {
                log.debug("微信退款查询 refundNo={} status={}", refundNo, s);
            }
            return null;
        } catch (Exception e) {
            log.warn("微信退款查询失败 refundNo={}: {}", refundNo, e.getMessage());
            return null;
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
