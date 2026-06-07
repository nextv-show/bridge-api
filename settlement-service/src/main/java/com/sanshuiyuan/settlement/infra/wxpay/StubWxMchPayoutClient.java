package com.sanshuiyuan.settlement.infra.wxpay;

import com.sanshuiyuan.settlement.domain.WithdrawalSplit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * V1 Stub：所有转账默认成功，返回模拟 batch_id/detail_id。
 * 可通过 system property "wxpay.payout.stub.fail" 切换为模拟失败。
 */
@Component
public class StubWxMchPayoutClient implements WxMchPayoutClient {
    private static final Logger log = LoggerFactory.getLogger(StubWxMchPayoutClient.class);
    private final AtomicLong counter = new AtomicLong(System.currentTimeMillis());

    @Override
    public PayoutResult transfer(WithdrawalSplit split) {
        if (Boolean.getBoolean("wxpay.payout.stub.fail")) {
            log.info("[stub] payout FAILED orderId={}", split.getOrderId());
            return PayoutResult.failed("STUB_FAILURE");
        }
        String batchId = "STUB_BATCH_" + counter.incrementAndGet();
        String detailId = UUID.randomUUID().toString().replace("-", "");
        log.info("[stub] payout OK orderId={} batchId={} amount={}", split.getOrderId(), batchId, split.getAmountCents());
        return PayoutResult.ok(batchId, detailId);
    }
}
