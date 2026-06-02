package com.sanshuiyuan.cend.wxmsg.event;

/**
 * 支付成功领域事件，由 PayCallbackUseCase 在事务提交后发布。
 * 字段仅包含推送模板消息所需的最小数据集，不含敏感身份信息。
 */
public class OrderPaidEvent {

    private final Long orderId;
    private final String openid;
    private final String orderNo;
    private final String modelName;
    private final long paidAmountCents;
    private final String cooldownEndAt;   // ISO-8601 字符串

    public OrderPaidEvent(Long orderId, String openid, String orderNo,
                          String modelName, long paidAmountCents, String cooldownEndAt) {
        this.orderId = orderId;
        this.openid = openid;
        this.orderNo = orderNo;
        this.modelName = modelName;
        this.paidAmountCents = paidAmountCents;
        this.cooldownEndAt = cooldownEndAt;
    }

    public Long getOrderId() { return orderId; }
    public String getOpenid() { return openid; }
    public String getOrderNo() { return orderNo; }
    public String getModelName() { return modelName; }
    public long getPaidAmountCents() { return paidAmountCents; }
    public String getCooldownEndAt() { return cooldownEndAt; }
}
