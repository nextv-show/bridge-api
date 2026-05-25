package com.sanshuiyuan.h5.wxmsg.event;

/**
 * 退款成功领域事件，由 WxRefundCallbackController 在退款确认后发布。
 */
public class RefundSucceededEvent {

    private final Long orderId;
    private final String openid;
    private final String orderNo;
    private final long refundAmountCents;

    public RefundSucceededEvent(Long orderId, String openid, String orderNo,
                                long refundAmountCents) {
        this.orderId = orderId;
        this.openid = openid;
        this.orderNo = orderNo;
        this.refundAmountCents = refundAmountCents;
    }

    public Long getOrderId() { return orderId; }
    public String getOpenid() { return openid; }
    public String getOrderNo() { return orderNo; }
    public long getRefundAmountCents() { return refundAmountCents; }
}
