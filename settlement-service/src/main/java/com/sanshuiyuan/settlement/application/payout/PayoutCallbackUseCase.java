package com.sanshuiyuan.settlement.application.payout;

import com.sanshuiyuan.settlement.domain.*;
import com.sanshuiyuan.settlement.infra.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** 微信代付回调用例：按 external_id 定位拆分，幂等更新 split/order/wallet。 */
@Component
public class PayoutCallbackUseCase {
    private static final Logger log = LoggerFactory.getLogger(PayoutCallbackUseCase.class);

    private final WithdrawalSplitRepository splitRepository;
    private final WithdrawalOrderRepository orderRepository;
    private final OwnerWalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;

    public PayoutCallbackUseCase(WithdrawalSplitRepository splitRepository,
                                  WithdrawalOrderRepository orderRepository,
                                  OwnerWalletRepository walletRepository,
                                  WalletLedgerRepository ledgerRepository) {
        this.splitRepository = splitRepository;
        this.orderRepository = orderRepository;
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * 处理微信回调。body 结构（简化）:
     * { "detail_id": "...", "batch_id": "...", "detail_status": "SUCCESS|FAIL" }
     */
    @Transactional
    public void handle(Map<String, Object> body) {
        String detailId = (String) body.get("detail_id");
        String status = (String) body.getOrDefault("detail_status", "FAIL");

        if (detailId == null) {
            log.warn("[payout] callback missing detail_id: {}", body);
            return;
        }

        // 按 external_id 查找 split
        var splits = splitRepository.findByExternalId(detailId);
        if (splits.isEmpty()) {
            log.warn("[payout] callback unknown detail_id={}", detailId);
            return;
        }

        WithdrawalSplit split = splits.get(0);
        if (split.getStatus() != SplitStatus.PAYING) {
            // 幂等：已处理过
            log.info("[payout] callback idempotent detailId={} currentStatus={}", detailId, split.getStatus());
            return;
        }

        WithdrawalOrder order = orderRepository.findById(split.getOrderId()).orElseThrow();

        if ("SUCCESS".equals(status)) {
            handleSuccess(split, order);
        } else {
            handleFailure(split, order, (String) body.getOrDefault("fail_reason", "WITHDRAWAL_FAILED_OTHER"));
        }
    }

    private void handleSuccess(WithdrawalSplit split, WithdrawalOrder order) {
        // 1. 标记 split PAID
        split.setStatus(SplitStatus.PAID);
        split.setPaidAt(LocalDateTime.now());
        splitRepository.save(split);

        // 2. 释放冻结余额（一次性整笔）
        OwnerWallet wallet = walletRepository.findById(order.getUserId()).orElseThrow();
        wallet.setFrozenCents(wallet.getFrozenCents() - order.getGrossCents());
        walletRepository.save(wallet);

        // 3. 标记订单 DONE
        order.setStatus(WithdrawalStatus.DONE);
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("[payout] callback SUCCESS orderId={} splitId={}", order.getId(), split.getId());
    }

    private void handleFailure(WithdrawalSplit split, WithdrawalOrder order, String reason) {
        // 1. 标记 split FAILED
        split.setStatus(SplitStatus.FAILED);
        split.setFailureReason(reason);
        splitRepository.save(split);

        // 2. 全额回退
        OwnerWallet wallet = walletRepository.findById(order.getUserId()).orElseThrow();
        wallet.setBalanceCents(wallet.getBalanceCents() + order.getGrossCents());
        wallet.setFrozenCents(wallet.getFrozenCents() - order.getGrossCents());
        walletRepository.save(wallet);

        // 3. 写退款 ledger
        WalletLedger refundLedger = new WalletLedger(order.getUserId(), LedgerDirection.IN,
                LedgerSourceType.WITHDRAWAL_REFUND, order.getId(),
                order.getGrossCents(), wallet.getBalanceCents());
        ledgerRepository.save(refundLedger);

        // 4. 订单 FAILED
        order.setStatus(WithdrawalStatus.FAILED);
        order.setFailureReason(reason);
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.warn("[payout] callback FAILED orderId={} reason={}", order.getId(), reason);
    }
}
