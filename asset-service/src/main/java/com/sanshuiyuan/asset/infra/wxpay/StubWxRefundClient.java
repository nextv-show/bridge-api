package com.sanshuiyuan.asset.infra.wxpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 退款 stub：dev/CI 缺凭证时使用。refund 仅记日志（视为已下达），
 * parseCallback / queryRefund 返回 null（不下结论），故订单留在 REFUNDING 等人工或真实环境处理。
 */
public class StubWxRefundClient implements WxRefundClient {

    private static final Logger log = LoggerFactory.getLogger(StubWxRefundClient.class);

    @Override
    public void refund(String outTradeNo, String refundNo, Long amountCents) {
        log.info("[stub] 退款 outTradeNo={} refundNo={} amount={}", outTradeNo, refundNo, amountCents);
    }

    @Override
    public RefundCallbackResult parseCallback(String body, String signature,
                                              String timestamp, String nonce, String serial) {
        log.info("[stub] 退款回调解析（stub 不解密，返回 null）");
        return null;
    }

    @Override
    public RefundCallbackResult queryRefund(String refundNo) {
        log.info("[stub] 退款查询 refundNo={}（stub 始终视为处理中）", refundNo);
        return null;
    }
}
