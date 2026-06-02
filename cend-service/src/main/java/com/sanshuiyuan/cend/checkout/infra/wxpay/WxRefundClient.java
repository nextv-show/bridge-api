package com.sanshuiyuan.cend.checkout.infra.wxpay;

public interface WxRefundClient {

    void refund(String outTradeNo, String refundNo, Long amountCents);

    RefundCallbackResult parseCallback(String body, String signature,
                                        String timestamp, String nonce, String serial);

    /**
     * 主动查询退款状态（兜底退款回调不达）。
     * <ul>
     *   <li>微信返 SUCCESS → 返回 {@code RefundCallbackResult(refundNo, wxRefundId, true)}；
     *   <li>微信返 CLOSED（退款已撤销，金额未实退）→ 返回 {@code RefundCallbackResult(refundNo, null, false)}；
     *   <li>PROCESSING / ABNORMAL / 未知 → 返回 {@code null}（保持订单 REFUNDING，等待下次轮询或人工介入）。
     * </ul>
     */
    RefundCallbackResult queryRefund(String refundNo);

    record RefundCallbackResult(String refundNo, String wxRefundId, boolean success) {}
}
