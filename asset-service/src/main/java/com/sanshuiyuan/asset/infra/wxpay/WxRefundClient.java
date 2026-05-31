package com.sanshuiyuan.asset.infra.wxpay;

/**
 * 购机订单微信支付 V3 退款抽象。配齐凭证时由 {@link SdkWxRefundClient} 真实发起，
 * 否则回退 {@link StubWxRefundClient}（dev/CI 不打微信）。装配见
 * {@link MpWxPayConfig#wxRefundClient()}。
 */
public interface WxRefundClient {

    /** 发起退款。outTradeNo 必须与支付时一致（购机 = 订单 id 左补零 10 位）。失败抛 RuntimeException。 */
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
