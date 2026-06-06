package com.sanshuiyuan.settlement.application;

import com.sanshuiyuan.settlement.application.guard.KycGuard;
import com.sanshuiyuan.settlement.application.guard.WithdrawalLimitGuard;
import com.sanshuiyuan.settlement.domain.LedgerDirection;
import com.sanshuiyuan.settlement.domain.LedgerSourceType;
import com.sanshuiyuan.settlement.domain.OwnerWallet;
import com.sanshuiyuan.settlement.domain.PaymentChannel;
import com.sanshuiyuan.settlement.domain.SplitKind;
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

/** 提现申请用例：KYC + 限额校验 → 幂等 → 拆分 → 扣余额冻结 → 写订单/分账/流水。 */
@Component
public class CreateWithdrawalUseCase {
    private static final Logger log = LoggerFactory.getLogger(CreateWithdrawalUseCase.class);

    private final KycGuard kycGuard;
    private final WithdrawalLimitGuard limitGuard;
    private final OwnerWalletRepository walletRepository;
    private final WithdrawalOrderRepository orderRepository;
    private final WithdrawalSplitRepository splitRepository;
    private final WalletLedgerRepository ledgerRepository;

    public CreateWithdrawalUseCase(KycGuard kycGuard, WithdrawalLimitGuard limitGuard,
                                   OwnerWalletRepository walletRepository,
                                   WithdrawalOrderRepository orderRepository,
                                   WithdrawalSplitRepository splitRepository,
                                   WalletLedgerRepository ledgerRepository) {
        this.kycGuard = kycGuard;
        this.limitGuard = limitGuard;
        this.walletRepository = walletRepository;
        this.orderRepository = orderRepository;
        this.splitRepository = splitRepository;
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * 创建提现。dryRun=true 时只预览不实际写库（返回计算后的临时 order，未持久化）。
     */
    @Transactional
    public WithdrawalOrder create(Long userId, Long grossCents, String clientRequestId, boolean dryRun) {
        // 1. KYC 校验
        kycGuard.verify(userId);

        // 2. 限额校验（同时取得当前手续费率）
        WithdrawalLimitGuard.WithdrawalLimit limit = limitGuard.verify(userId, grossCents);

        // 3. 幂等 — 同 user + client_request_id 返回历史订单
        var existing = orderRepository.findByUserIdAndClientRequestId(userId, clientRequestId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 4. 拆分：fee = floor(gross * feeBp/10000), cash = gross - fee（V1 feeBp=200 即 2%）
        long feeCents = grossCents * limit.feeBp() / 10000;
        long cashCents = grossCents - feeCents;

        if (dryRun) {
            // 预览模式：不写库，返回临时 order 对象
            return new WithdrawalOrder(userId, grossCents, feeCents, cashCents,
                    WithdrawalStatus.PENDING, clientRequestId);
        }

        // 5. 锁定余额（PESSIMISTIC_WRITE + balance >= gross）
        OwnerWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("WALLET_NOT_FOUND"));
        if (wallet.getBalanceCents() < grossCents) {
            throw new IllegalArgumentException("WALLET_INSUFFICIENT");
        }

        long balanceBefore = wallet.getBalanceCents();
        wallet.setBalanceCents(balanceBefore - grossCents);
        wallet.setFrozenCents(wallet.getFrozenCents() + grossCents);
        walletRepository.save(wallet);

        // 6. 写 withdrawal_order
        WithdrawalOrder order = new WithdrawalOrder(userId, grossCents, feeCents, cashCents,
                WithdrawalStatus.PROCESSING, clientRequestId);
        orderRepository.save(order);

        // 7. 写 withdrawal_split (CASH → QUEUED, 立即可代付)
        WithdrawalSplit split = new WithdrawalSplit(order.getId(), SplitKind.CASH, cashCents,
                PaymentChannel.WX_MCH_PAYOUT, SplitStatus.QUEUED, 0);
        split.setNextRunAt(LocalDateTime.now());
        splitRepository.save(split);

        // 8. 写 wallet_ledger (OUT, WITHDRAWAL_FREEZE)
        WalletLedger ledger = new WalletLedger(userId, LedgerDirection.OUT,
                LedgerSourceType.WITHDRAWAL_FREEZE, order.getId(), grossCents, wallet.getBalanceCents());
        ledgerRepository.save(ledger);

        log.info("Withdrawal created userId={} orderId={} gross={} fee={} cash={}",
                userId, order.getId(), grossCents, feeCents, cashCents);

        return order;
    }
}
