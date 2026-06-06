package com.sanshuiyuan.settlement.application.payout;

import com.sanshuiyuan.settlement.domain.*;
import com.sanshuiyuan.settlement.infra.repository.*;
import com.sanshuiyuan.settlement.infra.wxpay.WxMchPayoutClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 代付轮询器：扫描 QUEUED 拆分 → 调微信商家转账 → PAYING / 失败回滚。 */
@Component
public class PayoutWorker {
    private static final Logger log = LoggerFactory.getLogger(PayoutWorker.class);
    private static final List<String> WECHAT_LIMIT_ERRORS = List.of("AMOUNT_LIMIT", "TRANSFER_QUOTA_EXCEED");

    private final WithdrawalSplitRepository splitRepository;
    private final WithdrawalOrderRepository orderRepository;
    private final OwnerWalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;
    private final WxMchPayoutClient payoutClient;

    public PayoutWorker(WithdrawalSplitRepository splitRepository,
                        WithdrawalOrderRepository orderRepository,
                        OwnerWalletRepository walletRepository,
                        WalletLedgerRepository ledgerRepository,
                        WxMchPayoutClient payoutClient) {
        this.splitRepository = splitRepository;
        this.orderRepository = orderRepository;
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.payoutClient = payoutClient;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void process() {
        List<WithdrawalSplit> queued = splitRepository
                .findByStatusAndNextRunAtBefore(SplitStatus.QUEUED, LocalDateTime.now());

        for (WithdrawalSplit split : queued) {
            try {
                WxMchPayoutClient.PayoutResult result = payoutClient.transfer(split);
                if (result.accepted()) {
                    // 微信已受理 → PAYING
                    split.setStatus(SplitStatus.PAYING);
                    split.setExternalId(result.detailId());
                    splitRepository.save(split);
                    log.info("[payout] transfer accepted orderId={} splitId={} detailId={}",
                            split.getOrderId(), split.getId(), result.detailId());
                } else {
                    // 微信拒绝 → 失败回滚
                    handlePayoutFailure(split, mapErrorCode(result.errorCode()));
                }
            } catch (Exception e) {
                // 网络/系统异常 → 重试
                handleRetry(split, e);
            }
        }
    }

    private void handlePayoutFailure(WithdrawalSplit split, String failureReason) {
        // 1. 标记 split 失败
        split.setStatus(SplitStatus.FAILED);
        split.setFailureReason(failureReason);
        splitRepository.save(split);

        // 2. 全额回退余额（gross 全部退回）
        WithdrawalOrder order = orderRepository.findById(split.getOrderId()).orElseThrow();
        OwnerWallet wallet = walletRepository.findById(order.getUserId()).orElseThrow();

        wallet.setBalanceCents(wallet.getBalanceCents() + order.getGrossCents());
        wallet.setFrozenCents(wallet.getFrozenCents() - order.getGrossCents());
        walletRepository.save(wallet);

        // 3. 写退款 ledger
        WalletLedger refundLedger = new WalletLedger(order.getUserId(), LedgerDirection.IN,
                LedgerSourceType.WITHDRAWAL_REFUND, order.getId(),
                order.getGrossCents(), wallet.getBalanceCents());
        ledgerRepository.save(refundLedger);

        // 4. 标记订单 FAILED
        order.setStatus(WithdrawalStatus.FAILED);
        order.setFailureReason(failureReason);
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.warn("[payout] order {} FAILED: {} (refunded {} cents)", order.getId(), failureReason, order.getGrossCents());
    }

    private void handleRetry(WithdrawalSplit split, Exception e) {
        int retries = split.getRetried() + 1;
        split.setRetried(retries);
        if (retries >= 10) {
            // 超过 10 次 → FAILED + 回退
            handlePayoutFailure(split, "RETRIES_EXCEEDED");
            log.error("[payout] split {} failed after {} retries", split.getId(), retries, e);
        } else {
            long backoffSeconds = Math.min(1L << retries, 512);
            split.setNextRunAt(LocalDateTime.now().plusSeconds(backoffSeconds));
            splitRepository.save(split);
            log.warn("[payout] split {} retry {} in {}s", split.getId(), retries, backoffSeconds);
        }
    }

    private String mapErrorCode(String wxErrorCode) {
        if (WECHAT_LIMIT_ERRORS.contains(wxErrorCode)) {
            return "WITHDRAWAL_FAILED_LIMIT";
        }
        return "WITHDRAWAL_FAILED_OTHER";
    }
}
