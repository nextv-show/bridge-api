package com.sanshuiyuan.settlement.application.payout;

import com.sanshuiyuan.settlement.domain.SplitStatus;
import com.sanshuiyuan.settlement.domain.WithdrawalSplit;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalSplitRepository;
import com.sanshuiyuan.settlement.infra.wxpay.transfer.WxTransferBillsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 转账对账兜底：对在途（PAYING / 残留 QUEUED）的提现拆分，用「已鉴权的查单」确定真状态并落地，
 * 不依赖（不可信的）回调内容。商家转账「用户确认」期间状态会停在 WAIT_USER_CONFIRM/PROCESSING，
 * 用户确认后转 SUCCESS、超时未确认由微信转 CANCELLED → 这里据此释放冻结或退款。
 */
@Component
public class PayoutWorker {
    private static final Logger log = LoggerFactory.getLogger(PayoutWorker.class);
    /** 查单连续 not-found 超过该次数 → 判失败退款（发起从未落地 / 单据丢失）。 */
    private static final int MAX_NOT_FOUND_RETRIES = 15;
    private static final long MAX_BACKOFF_SECONDS = 300;

    private final WithdrawalSplitRepository splitRepository;
    private final WxTransferBillsClient transferClient;
    private final PayoutMoneyOps moneyOps;

    public PayoutWorker(WithdrawalSplitRepository splitRepository,
                        WxTransferBillsClient transferClient,
                        PayoutMoneyOps moneyOps) {
        this.splitRepository = splitRepository;
        this.transferClient = transferClient;
        this.moneyOps = moneyOps;
    }

    @Scheduled(fixedDelay = 3000)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        List<WithdrawalSplit> due = new ArrayList<>();
        due.addAll(splitRepository.findByStatusAndNextRunAtBefore(SplitStatus.PAYING, now));
        due.addAll(splitRepository.findByStatusAndNextRunAtBefore(SplitStatus.QUEUED, now));
        for (WithdrawalSplit split : due) {
            try {
                poll(split);
            } catch (Exception e) {
                log.error("[payout] reconcile splitId={} 异常: {}", split.getId(), e.toString());
                moneyOps.scheduleRetry(split.getId(), backoff(split.getRetried() + 1));
            }
        }
    }

    private void poll(WithdrawalSplit split) {
        // 优先用受理时落库的权威单号，免疫 PayoutBillNo 格式漂移；为空（受理前/历史行）再回退重算。
        String outBillNo = split.getOutBillNo() != null
                ? split.getOutBillNo()
                : PayoutBillNo.of(split.getOrderId(), split.getId());
        WxTransferBillsClient.QueryResult q = transferClient.queryByOutBillNo(outBillNo);

        if (!q.found()) {
            if (split.getRetried() + 1 >= MAX_NOT_FOUND_RETRIES) {
                log.warn("[payout] 查单连续未找到 outBillNo={}，判失败退款", outBillNo);
                moneyOps.refundOnFailure(split.getOrderId(), "WX_NOT_FOUND");
            } else {
                moneyOps.scheduleRetry(split.getId(), backoff(split.getRetried() + 1));
            }
            return;
        }

        String state = q.state() == null ? "" : q.state();
        switch (state) {
            case "SUCCESS" -> moneyOps.releaseOnSuccess(split.getOrderId());
            case "FAIL", "CANCELLED" -> moneyOps.refundOnFailure(split.getOrderId(),
                    "WX_" + state + (q.failReason() != null ? ":" + q.failReason() : ""));
            default -> {
                // ACCEPTED / PROCESSING / WAIT_USER_CONFIRM / TRANSFERING / CANCELING：继续等待
                if (split.getStatus() == SplitStatus.QUEUED) {
                    moneyOps.promoteToPaying(split.getId(), q.transferBillNo());
                }
                moneyOps.scheduleRetry(split.getId(), backoff(split.getRetried() + 1));
            }
        }
    }

    private long backoff(int retries) {
        return Math.min(1L << Math.min(retries, 9), MAX_BACKOFF_SECONDS);
    }
}
