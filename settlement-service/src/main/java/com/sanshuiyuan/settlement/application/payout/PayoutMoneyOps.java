package com.sanshuiyuan.settlement.application.payout;

import com.sanshuiyuan.settlement.domain.LedgerDirection;
import com.sanshuiyuan.settlement.domain.LedgerSourceType;
import com.sanshuiyuan.settlement.domain.OwnerWallet;
import com.sanshuiyuan.settlement.domain.SplitStatus;
import com.sanshuiyuan.settlement.domain.WalletLedger;
import com.sanshuiyuan.settlement.domain.WithdrawalOrder;
import com.sanshuiyuan.settlement.domain.WithdrawalSplit;
import com.sanshuiyuan.settlement.domain.WithdrawalStatus;
import com.sanshuiyuan.settlement.infra.repository.OwnerWalletRepository;
import com.sanshuiyuan.settlement.infra.repository.WalletLedgerRepository;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalOrderRepository;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalSplitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提现资金操作单点（钱包冻结/解冻/退款、split/order 状态机）。所有方法幂等：
 * 终态订单不再变动，避免回调/查单/重试重复改钱包。
 */
@Component
public class PayoutMoneyOps {
    private static final Logger log = LoggerFactory.getLogger(PayoutMoneyOps.class);

    private final WithdrawalOrderRepository orderRepository;
    private final WithdrawalSplitRepository splitRepository;
    private final OwnerWalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;

    public PayoutMoneyOps(WithdrawalOrderRepository orderRepository,
                          WithdrawalSplitRepository splitRepository,
                          OwnerWalletRepository walletRepository,
                          WalletLedgerRepository ledgerRepository) {
        this.orderRepository = orderRepository;
        this.splitRepository = splitRepository;
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
    }

    /** 转账受理：split QUEUED→PAYING，回填商户单号 + 转账单号 + package。 */
    @Transactional
    public void recordAccepted(Long splitId, String outBillNo, String transferBillNo, String packageInfo) {
        WithdrawalSplit split = splitRepository.findById(splitId).orElseThrow();
        if (split.getStatus() != SplitStatus.QUEUED) {
            return; // 幂等
        }
        split.setStatus(SplitStatus.PAYING);
        split.setOutBillNo(outBillNo);
        split.setTransferBillNo(transferBillNo);
        split.setExternalId(transferBillNo);
        split.setPackageInfo(packageInfo);
        split.setNextRunAt(LocalDateTime.now().plusSeconds(30));
        splitRepository.save(split);
        log.info("[payout] accepted splitId={} transferBillNo={}", splitId, transferBillNo);
    }

    /** 查到设备仍在处理中、但本地还是 QUEUED：补提升为 PAYING（兜底 record 丢失）。 */
    @Transactional
    public void promoteToPaying(Long splitId, String transferBillNo) {
        WithdrawalSplit split = splitRepository.findById(splitId).orElseThrow();
        if (split.getStatus() != SplitStatus.QUEUED) {
            return;
        }
        split.setStatus(SplitStatus.PAYING);
        if (transferBillNo != null) {
            split.setTransferBillNo(transferBillNo);
            split.setExternalId(transferBillNo);
        }
        splitRepository.save(split);
    }

    /** 转账成功：split→PAID，释放冻结，order→DONE。幂等。 */
    @Transactional
    public void releaseOnSuccess(Long orderId) {
        WithdrawalOrder order = orderRepository.findById(orderId).orElseThrow();
        if (order.getStatus() == WithdrawalStatus.DONE) {
            return; // 幂等
        }
        if (order.getStatus() == WithdrawalStatus.FAILED) {
            log.error("[payout] 异常：order {} 已 FAILED 却又收到 SUCCESS，忽略（需人工核查）", orderId);
            return;
        }
        WithdrawalSplit split = firstSplit(orderId);
        if (split != null && split.getStatus() != SplitStatus.PAID) {
            split.setStatus(SplitStatus.PAID);
            split.setPaidAt(LocalDateTime.now());
            splitRepository.save(split);
        }
        OwnerWallet wallet = walletRepository.findById(order.getUserId()).orElseThrow();
        wallet.setFrozenCents(wallet.getFrozenCents() - order.getGrossCents());
        walletRepository.save(wallet);

        order.setStatus(WithdrawalStatus.DONE);
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("[payout] SUCCESS order={} 释放冻结 {} 分", orderId, order.getGrossCents());
    }

    /** 转账失败/撤销：split→FAILED，全额退回余额 + 写退款流水，order→FAILED。幂等。 */
    @Transactional
    public void refundOnFailure(Long orderId, String reason) {
        WithdrawalOrder order = orderRepository.findById(orderId).orElseThrow();
        if (order.getStatus() == WithdrawalStatus.FAILED || order.getStatus() == WithdrawalStatus.DONE) {
            return; // 幂等 / 终态
        }
        WithdrawalSplit split = firstSplit(orderId);
        if (split != null && split.getStatus() != SplitStatus.FAILED && split.getStatus() != SplitStatus.PAID) {
            split.setStatus(SplitStatus.FAILED);
            split.setFailureReason(reason);
            splitRepository.save(split);
        }
        OwnerWallet wallet = walletRepository.findById(order.getUserId()).orElseThrow();
        wallet.setBalanceCents(wallet.getBalanceCents() + order.getGrossCents());
        wallet.setFrozenCents(wallet.getFrozenCents() - order.getGrossCents());
        walletRepository.save(wallet);

        WalletLedger refund = new WalletLedger(order.getUserId(), LedgerDirection.IN,
                LedgerSourceType.WITHDRAWAL_REFUND, order.getId(),
                order.getGrossCents(), wallet.getBalanceCents());
        ledgerRepository.save(refund);

        order.setStatus(WithdrawalStatus.FAILED);
        order.setFailureReason(reason);
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.warn("[payout] FAILED order={} 退回 {} 分 reason={}", orderId, order.getGrossCents(), reason);
    }

    /** 退避重排（同时累加 retried 计数）。 */
    @Transactional
    public void scheduleRetry(Long splitId, long backoffSeconds) {
        WithdrawalSplit split = splitRepository.findById(splitId).orElseThrow();
        split.setRetried(split.getRetried() + 1);
        split.setNextRunAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        splitRepository.save(split);
    }

    /** 收到任意转账回调时把在途单催到立即查单（回调内容加密不可信，仅作触发；真状态以查单为准）。 */
    @Transactional
    public void nudgeAllInFlight() {
        LocalDateTime now = LocalDateTime.now();
        List<WithdrawalSplit> inflight = splitRepository.findByStatusAndNextRunAtBefore(SplitStatus.PAYING,
                now.plusYears(10));
        for (WithdrawalSplit s : inflight) {
            s.setNextRunAt(now);
        }
        splitRepository.saveAll(inflight);
    }

    private WithdrawalSplit firstSplit(Long orderId) {
        List<WithdrawalSplit> splits = splitRepository.findByOrderId(orderId);
        return splits.isEmpty() ? null : splits.get(0);
    }
}
